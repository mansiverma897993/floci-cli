package io.floci.cli.docker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Thin subprocess wrapper around the docker CLI.
 * Subprocess invocation avoids reflection config needed by docker-java on native image.
 */
public class DockerClient {

    public record ContainerInfo(
            String id,
            String name,
            String image,
            String status,
            String state,
            String ports) {}

    public record ImageInfo(String id, String repository, String tag, String digest) {}

    public String dockerVersion() throws DockerException {
        return run("docker", "version", "--format", "{{.Server.Version}}").trim();
    }

    public boolean isDaemonReachable() {
        try {
            int code = runProcess("docker", "info", "--format", "{{.ServerVersion}}").waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<ContainerInfo> inspectContainer(String name) throws DockerException {
        try {
            String out = run("docker", "inspect", "--format",
                    "{{.Id}}|{{.Name}}|{{.Config.Image}}|{{.State.Status}}|{{.State.Status}}|{{range $p, $b := .NetworkSettings.Ports}}{{if $b}}{{(index $b 0).HostPort}}->{{$p}} {{end}}{{end}}",
                    name);
            if (out.isBlank()) return Optional.empty();
            String[] parts = out.trim().split("\\|", -1);
            return Optional.of(new ContainerInfo(
                    parts.length > 0 ? parts[0] : "",
                    parts.length > 1 ? parts[1].replaceFirst("^/", "") : name,
                    parts.length > 2 ? parts[2] : "",
                    parts.length > 3 ? parts[3] : "",
                    parts.length > 4 ? parts[4] : "",
                    parts.length > 5 ? parts[5].trim() : ""));
        } catch (DockerException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("no such")) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public boolean isImagePresent(String image) throws DockerException {
        try {
            String out = run("docker", "images", "-q", image);
            return !out.isBlank();
        } catch (DockerException e) {
            return false;
        }
    }

    public Optional<String> imageDigest(String image) throws DockerException {
        try {
            String out = run("docker", "inspect", "--format", "{{index .RepoDigests 0}}", image);
            return out.isBlank() ? Optional.empty() : Optional.of(out.trim());
        } catch (DockerException e) {
            return Optional.empty();
        }
    }

    public void pull(String image, String policy) throws DockerException {
        if ("never".equalsIgnoreCase(policy)) return;
        if ("missing".equalsIgnoreCase(policy) && isImagePresent(image)) return;
        runStreaming("docker", "pull", image);
    }

    public String startContainer(List<String> args) throws DockerException {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.addAll(args);
        return run(cmd.toArray(String[]::new)).trim();
    }

    public void stopContainer(String name, int timeout) throws DockerException {
        run("docker", "stop", "--time", String.valueOf(timeout), name);
    }

    public void removeContainer(String name) throws DockerException {
        run("docker", "rm", name);
    }

    public void streamLogs(String name, boolean follow, int tail, String since) throws DockerException {
        List<String> cmd = new ArrayList<>(List.of("docker", "logs"));
        if (follow) cmd.add("--follow");
        if (tail > 0) { cmd.add("--tail"); cmd.add(String.valueOf(tail)); }
        if (since != null && !since.isBlank()) { cmd.add("--since"); cmd.add(since); }
        cmd.add(name);
        runStreaming(cmd.toArray(String[]::new));
    }

    private String run(String... cmd) throws DockerException {
        try {
            Process proc = runProcess(cmd);
            String stdout = new String(proc.getInputStream().readAllBytes());
            String stderr = new String(proc.getErrorStream().readAllBytes());
            int code = proc.waitFor();
            if (code != 0) {
                throw new DockerException(stderr.isBlank() ? stdout : stderr);
            }
            return stdout;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerException("Interrupted");
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("No such file")) {
                throw new DockerException("docker binary not found in PATH. Install Docker Desktop or Docker Engine.");
            }
            throw new DockerException(e.getMessage());
        }
    }

    private void runStreaming(String... cmd) throws DockerException {
        try {
            Process proc = runProcess(cmd);
            Thread out = new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    reader.lines().forEach(System.out::println);
                } catch (IOException ignored) {}
            });
            Thread err = new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                    reader.lines().forEach(System.err::println);
                } catch (IOException ignored) {}
            });
            out.start();
            err.start();
            int code = proc.waitFor();
            out.join();
            err.join();
            if (code != 0) {
                throw new DockerException("docker command failed with exit code " + code);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerException("Interrupted");
        } catch (IOException e) {
            throw new DockerException(e.getMessage());
        }
    }

    private Process runProcess(String... cmd) throws IOException {
        return new ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start();
    }

    public static boolean isInstalled() {
        try {
            Process p = new ProcessBuilder("docker", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static String socketPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "\\\\.\\pipe\\docker_engine";
        return System.getenv().getOrDefault("DOCKER_SOCK", "/var/run/docker.sock");
    }
}

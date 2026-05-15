package io.floci.cli;

import io.floci.cli.commands.*;
import io.floci.cli.commands.config.ConfigCommand;
import io.floci.cli.commands.snapshot.SnapshotCommand;
import io.floci.cli.output.Ansi;
import picocli.CommandLine;
import picocli.CommandLine.*;

@Command(
        name = "floci",
        description = "Manage your local Floci AWS emulator%n%n" +
                "  floci start     — launch the container%n" +
                "  floci stop      — stop the container%n" +
                "  floci status    — show health and version%n" +
                "  floci doctor    — diagnose environment issues%n" +
                "  floci logs      — stream container logs%n" +
                "  floci env       — print AWS environment variables%n",
        mixinStandardHelpOptions = true,
        versionProvider = FlociCli.VersionProvider.class,
        subcommands = {
                StartCommand.class,
                StopCommand.class,
                RestartCommand.class,
                StatusCommand.class,
                LogsCommand.class,
                WaitCommand.class,
                VersionCommand.class,
                ServicesCommand.class,
                DoctorCommand.class,
                EnvCommand.class,
                ConfigCommand.class,
                SnapshotCommand.class,
                CompletionCommand.class,
                HelpCommand.class
        }
)
public class FlociCli implements Runnable {

    static class VersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{"floci " + VersionCommand.CLI_VERSION};
        }
    }

    static class ExceptionHandler implements IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine cmd, ParseResult parseResult) {
            System.err.println(Ansi.red("Error: ") + ex.getMessage());
            if (Boolean.getBoolean("floci.verbose")) {
                ex.printStackTrace(System.err);
            }
            return 1;
        }
    }

    public static void main(String[] args) {
        // Honor NO_COLOR and non-TTY environments
        if (System.getenv("NO_COLOR") != null || System.console() == null) {
            Ansi.disable();
        }
        int exitCode = new CommandLine(new FlociCli())
                .setExecutionExceptionHandler(new ExceptionHandler())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}

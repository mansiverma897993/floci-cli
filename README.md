# floci-cli

Official command-line interface for [Floci](https://floci.io) — the free, open-source local AWS emulator.

```sh
floci start
floci doctor
eval $(floci env)
aws s3 mb s3://my-bucket
```

## Installation

### Homebrew (macOS / Linux)

```sh
brew install floci-io/floci/floci
```

### Install script (Linux / macOS)

```sh
curl -fsSL https://floci.io/install.sh | sh
```

### Windows (PowerShell)

```powershell
iwr https://floci.io/install.ps1 | iex
```

### Scoop (Windows)

```powershell
scoop bucket add floci https://github.com/floci-io/scoop-floci
scoop install floci
```

### JVM fallback

Download `floci.jar` from the [latest release](https://github.com/floci-io/floci-cli/releases/latest) and run:

```sh
java -jar floci.jar version
```

---

## Quick Start

```sh
# Start Floci
floci start

# Check environment
floci doctor

# Export AWS environment variables
eval $(floci env)

# Use AWS services normally
aws s3 mb s3://my-bucket
aws dynamodb create-table --table-name users \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

# Stop Floci
floci stop
```

---

## Command Reference

| Command | Description |
|---------|-------------|
| `floci start` | Launch the Floci container |
| `floci stop` | Stop (and optionally remove) the container |
| `floci restart` | Stop then start |
| `floci status` | Show container state and server health |
| `floci logs` | Stream container logs |
| `floci wait` | Poll until Floci is ready (CI-friendly) |
| `floci version` | Show CLI and server versions |
| `floci services` | List enabled AWS services |
| `floci doctor` | Run environment diagnostics |
| `floci env` | Print AWS environment variables |
| `floci config show` | Show active configuration |
| `floci config validate` | Validate a docker-compose.yml |
| `floci config profile` | Manage named profiles |
| `floci snapshot save/load/list/delete` | Manage state snapshots |
| `floci completion bash\|zsh` | Generate shell completion |

All commands support `--help`. Global flags available on every command:

```
--endpoint <url>         Floci server URL     (default: http://localhost:4566, env: FLOCI_ENDPOINT)
--container <name>       Container name       (default: floci, env: FLOCI_CONTAINER)
--output|-o text|json|yaml  Output format     (default: text)
--quiet, -q              Suppress non-error output
--verbose, -v            Debug logging to stderr
--no-color               Disable ANSI colors
--profile <name>         Load settings from ~/.floci/profiles/<name>.yaml
```

> **Port auto-detection** — `status`, `version`, and `wait` automatically derive the correct
> endpoint from the container's port mapping. You don't need to pass `--endpoint` when using
> a non-default port, as long as `--container` points to the right container.

---

## Commands

### `floci start`

Pulls the image (if needed), starts the container, and waits for readiness.

```sh
floci start                          # default port 4566
floci start --port 4599              # custom host port
floci start --services s3,dynamodb   # enable specific services
floci start --persist ./data         # persist state to a host directory
floci start --pull always            # always pull the latest image
floci start --detach                 # return immediately, don't wait
```

### `floci stop`

```sh
floci stop                    # graceful stop (10s timeout)
floci stop --timeout 30       # wait up to 30s before force-kill
floci stop --remove           # also remove the container after stopping
```

### `floci status`

```sh
floci status                          # auto-detects endpoint from container port mapping
floci status --container myfloci      # target a specific container
floci status -o json                  # structured output
```

### `floci env`

Prints AWS environment variables pointing at the running Floci instance. The default
hostname is `localhost.floci.io` (resolves to `127.0.0.1`, enables virtual-hosted S3 bucket names).

```sh
eval $(floci env)                          # bash/zsh — sets all four AWS vars
floci env --shell fish | source            # fish
floci env --shell powershell | Invoke-Expression  # PowerShell

floci env --host myhost.local              # custom hostname
floci env --region eu-west-1              # custom region (default: us-east-1)
floci env --endpoint http://localhost:4599 # pick up port from a non-default instance
floci env -o json                          # structured output for scripts
```

Variables exported:

| Variable | Default value |
|----------|---------------|
| `AWS_ENDPOINT_URL` | `http://localhost.floci.io:4566` |
| `AWS_ACCESS_KEY_ID` | `test` |
| `AWS_SECRET_ACCESS_KEY` | `test` |
| `AWS_DEFAULT_REGION` | `us-east-1` |

### `floci logs`

```sh
floci logs                       # last logs from the container
floci logs --tail 50             # last 50 lines
floci logs --since 5m            # logs from the last 5 minutes
floci logs --follow              # stream live logs (Ctrl-C to stop)
```

### `floci wait`

```sh
floci wait                        # wait up to 30s (default)
floci wait --timeout 2m           # custom timeout (supports s, m, h)
floci wait --service dynamodb     # wait until a specific service is ready
floci wait -o json                # machine-readable output
```

### `floci doctor`

```sh
floci doctor                      # run all checks
floci doctor --check docker.installed   # run a single check by name
floci doctor --fix                # auto-fix fixable issues
floci doctor -o json              # structured output for scripts
```

### `floci version`

```sh
floci version                     # CLI version, server version, image digest
floci version -o json
```

### `floci services`

```sh
floci services                    # list all enabled services
floci services --mode docker      # only docker-backed services
floci services --mode in-process  # only in-process services
floci services -o json
```

### `floci config`

```sh
floci config show                          # show active configuration
floci config profile list                  # list saved profiles
floci config profile create <name>         # create a new profile
floci config profile show <name>           # show a profile
floci config profile delete <name>         # delete a profile
floci config validate -f docker-compose.yml  # validate a Compose file
```

Profiles are stored in `~/.floci/profiles/<name>.yaml` and can override any global option.
Use `--profile <name>` on any command to load one.

### `floci snapshot`

Save and restore named snapshots of Floci state (requires server-side support — coming soon).

```sh
floci snapshot list
floci snapshot save <name> --message "before migration"
floci snapshot load <name>
floci snapshot delete <name>
floci snapshot export <name> -o tarball.tar.gz
floci snapshot import tarball.tar.gz
```

### `floci completion`

```sh
floci completion bash >> ~/.bashrc
floci completion zsh  >> ~/.zshrc
```

---

## CI Usage

```sh
floci start --port 4566 --detach
floci wait --timeout 60s
pytest  # or your test command
floci stop --remove
```

Set AWS environment variables in CI before running tests:

```sh
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
```

With Docker Compose:

```yaml
services:
  floci:
    image: floci/floci:latest
    ports:
      - "4566:4566"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
```

---

## Scope

`floci-cli` manages Floci's lifecycle, config, state, and diagnostics.
It does **not** wrap the AWS CLI or manage AWS resources.
Use `aws` with `AWS_ENDPOINT_URL=http://localhost:4566` for resource operations.

---

## License

MIT — see [LICENSE](LICENSE).

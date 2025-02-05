# Deployment 

## Docker compose

If you do not have a kubernetes cluster available and do not wish to set one up, you can run the application using
docker compose.

simply install the image using the `build.sh` script, or manually build the uber jar and install the image.  The following
can be used from Linux/Mac from the repository root -

```bash
./gradlew build
docker build -f deploy/Dockerfile -t moonkev/vertx_playground:1.0 .
```

After installing the image, simple run `docker compose up` from the `/deploy` directory.  The compose file exposes
the service apps on the following ports on your host machine so that you can access the services from apps 
outside of docker -

1) rest-service: 18080
2) graphql-service: 28080
3) grpc-service: 38080

## Kubernetes with helm

The application can be deployed to a kubernetes cluster using supplied helm charts located in the `/deploy/helm` directory.
The following instrucions detail how to get a k8s cluster running and to deploy using helm.

### install and run `minikube`

While any kubernetes cluster should suffice, this was tested using minikube, which installs a full running k8s
cluster that runs on a single machine.

See [Minikube Getting Started](https://minikube.sigs.k8s.io/docs/start/?arch=%2Flinux%2Fx86-64%2Fstable%2Fbinary+download)

Once minikube is installed, you will want to install the container into minikube's docker repository as it will not
pull container images from a local running docker normally (it can be configured to, but I will not recommend that here).
You can either run the `build-minikube.sh` script located in the deploy directory or you can manually build the uber
jar and run the commands.  The following will work on Linux/Mac if run from the repository root -

```bash
./gradlew build
eval $(minikube -p minikube docker-env)
docker build -f deploy/Dockerfile -t moonkev/vertx_playground:1.0 .
```

### Install `helm`

See [Installing Helm](https://helm.sh/docs/intro/install/)

#### Basic Helm Commands

See [Helm notebook](../tests/notebooks/helm.ipynb) for demonstration of commands

##### 1. Install a Chart

To install a chart into your Kubernetes cluster:

```bash
helm install [release] [chart] [flags]
```

Example:

- Installing the `fibonacci-worker` chart into a namespace

```bash
helm install fibonacci-worker ./fibonacci-worker --namespace vertx-playground
```

##### 2. Unstall a Release

To uninstall (delete) a release:

```bash
helm uninstall [release] [chart]
```

Example:

- Uninstalling the `fibonacci-worker` chart

```bash
helm uninstall fibonacci-worker 
```

##### 3. Upgrade a Release

To upgrade an existing release

```bash
helm upgrade fibonacci-worker
```

##### 4. Install or Upgrade a Release (`upgrade --install`)

If you want to ensure the Helm installs the release if it doesn't exist, or upgrade it if it does (very useful)

```bash
helm upgrade --install [release-name] [chart-name]
```

Example:

To ensure `fibonacci-worker` is either upgrade or installed

```bash
helm upgrade --install fibonacci-worker ./fibonacci-worker
```

Or in a namespace

```bash
helm upgrade --install fibonacci-worker ./fibonacci-worker --namespace vertx-playground
```

##### 5. List Installed Releases

To list all installed Helm releases:

```bash
helm list
```

Or for a specific namespace

```bash
helm list --namespace vertx-playground
```

##### 6. Rollback a Release

If you need to rollback to a previous release version:

```bash
helm rollback [release-name] [revision-number]
```

Example:

```bash
helm rollback fibonacci-worker 1
```

---

##### Install the 'umbrella` chart

To install everything currently configured in **vertx-playground**

**Note** - this will create a release called ***vertx-playground-full*** and install the containers into a namespace called ***vertx-playground***.
It will create the namespace if it doesn't exist.

```bash
helm install vertx-playground-full ./vertx-playground-full -n vertx-playground --create-namespace
```

##### Install and set parameters for `fibonacci-worker`

This will set the image *version* to use as **latest**, and the *trainSize* to **500**

```bash
helm install vertx-playground-full ./vertx-playground-full -n vertx-playground --create-namespace --set fibonacci-worker.image.tag=latest --set grpc-service.service.port=18080
```

---

##### Updating Dependencies for the full chart

If you modify any of the sub charts, you'll need to update the dependencies for the `vertx-playground-full` chart

- Update the dependencies

```bash
helm dependency update ./vertx-playground-full
```

- Build the dependencies

```bash
helm dependency build ./vertx-playground-full
```

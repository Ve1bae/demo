# Hangyin Kubernetes deployment

This directory deploys the local full stack to the Docker Desktop Kubernetes cluster:

- `mysql.yml`: main application database, backed by `hangyin-mysql-data`.
- `live-mysql.yml`: live-service database, backed by `hangyin-live-mysql-data`.
- `srs.yml`: RTMP/HTTP-FLV/SRS API.
- `minio.yml`: object storage, backed by `hangyin-minio-data`.
- `backend.yml`, `live-service.yml`: Spring services.
- `frontend.yml`: Vue application served by Nginx, proxying API/WebSocket/video traffic.

Build and load images into Docker Desktop's Kind node from this directory's parent:

```powershell
docker build -t hangyin/backend:0.1.0 .\backend
docker build -t hangyin/live-service:0.1.0 .\live-service
docker build -t hangyin/frontend:0.1.0 .\frontend\demo
kind load docker-image hangyin/backend:0.1.0 hangyin/live-service:0.1.0 hangyin/frontend:0.1.0 --name desktop
```

Create the namespace first, then runtime secrets and SQL ConfigMaps (do not commit the generated Secret):

```powershell
kubectl apply -f .\k8s\namespace.yml

$env:DB_PASSWORD = [guid]::NewGuid().ToString('N')
kubectl -n hangyin create secret generic hangyin-secrets `
  --from-literal=db-password=$env:DB_PASSWORD `
  --from-literal=minio-user=minioadmin `
  --from-literal=minio-password=minioadmin `
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n hangyin create configmap live-db-init `
  --from-file=001_live_schema.sql=live-service/sql/001_live_schema.sql `
  --dry-run=client -o yaml | kubectl apply -f -
```

Create the main database SQL ConfigMap from the repository schema:

```powershell
kubectl -n hangyin create configmap hangyin-main-db-init `
  --from-file=001_demo.sql=demo.sql `
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f .\k8s\configmaps.yml
```

Deploy and verify:

```powershell
kubectl apply -f .\k8s\pvc.yml
kubectl apply -f .\k8s\configmaps.yml
kubectl apply -f .\k8s\mysql.yml
kubectl apply -f .\k8s\live-mysql.yml
kubectl apply -f .\k8s\srs.yml
kubectl apply -f .\k8s\minio.yml
kubectl apply -f .\k8s\backend.yml
kubectl apply -f .\k8s\live-service.yml
kubectl apply -f .\k8s\frontend.yml
kubectl -n hangyin rollout status deployment/mysql --timeout=240s
kubectl -n hangyin rollout status deployment/live-mysql --timeout=240s
kubectl -n hangyin rollout status deployment/srs --timeout=180s
kubectl -n hangyin rollout status deployment/backend --timeout=240s
kubectl -n hangyin rollout status deployment/live-service --timeout=240s
kubectl -n hangyin rollout status deployment/frontend --timeout=180s
kubectl -n hangyin port-forward service/frontend 8080:80
kubectl -n hangyin port-forward service/srs 1935:1935
```

Then open `http://127.0.0.1:8080`. Keep the SRS port-forward running when publishing to
the RTMP URL returned by the live-service. The live-service API can be checked directly with
`kubectl -n hangyin port-forward service/live-service 8090:8090`.

The `Live Service CI/CD` GitHub Actions workflow builds commit-versioned backend,
live-service and frontend images, creates a disposable Kind cluster, provisions PVC-backed
MySQL and MinIO, deploys SRS and all application workloads, then publishes a generated test
stream and verifies the frontend, backend API, live API, SRS API and HTTP-FLV path.

The PVCs use Docker Desktop's default `local-path` storage class. They are suitable for
local verification; production should use managed MySQL/MinIO or a replicated storage class.

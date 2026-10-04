# Git Finalization

Check current state:

```powershell
git status --short
```

View history:

```powershell
git log --oneline --graph --decorate -20
```

Commit observability:

```powershell
git add src/main/java/com/seatreservation/seat_reservation_service/web
git add src/main/java/com/seatreservation/seat_reservation_service/metrics
git add src/main/resources/application.properties

git commit -m "obs: add request correlation and Prometheus business metrics"
```

Commit tests:

```powershell
git add load-test

git commit -m "test: add final concurrency and burst load tests"
```

Commit Docker:

```powershell
git add docker

git commit -m "build: dockerize multi-instance reservation service"
```

Commit documentation:

```powershell
git add README.md
git add WRITEUP.md
git add docs

git commit -m "docs: add architecture validation and deployment guide"
```

Inspect:

```powershell
git status
git log --oneline --graph --decorate -15
```

Push:

```powershell
git push origin HEAD
```

Do not squash all development into one commit.

An incremental commit history provides useful evidence of the engineering process.
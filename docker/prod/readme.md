## Development
1. go to `./docker/dev`
2. execute `docker-compose up` and keep the command on frontend
3. pass VM args `-Dspring.profiles.active=dev` to the running configuration.

## Production (Docker base)
1. go to `./docker/prod`
2. execute `docker-compose up -d --build`
# deploy/helm

The chart for this repo's own services (`bpo-worker-*`, `bpo-process-api`, `bpo-event-bridge`)
lands here with the first deployable service, with the platform guard vendored as
`templates/_fbx_helpers.tpl` and checked by sha256, like every other service chart. The Camunda
engine itself is deployed from `deploy/camunda-platform`.

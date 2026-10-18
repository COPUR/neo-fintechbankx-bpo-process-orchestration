package fixture.violating;

import io.camunda.client.annotation.JobWorker;

/** Declares a job worker: the domain service would now be an orchestration worker. */
public class JobWorkerAnnotation {

    @JobWorker(type = "pay.capture")
    public void capture() {
    }
}

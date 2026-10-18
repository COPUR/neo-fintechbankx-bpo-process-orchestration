package fixture.violating;

import io.camunda.client.exception.CamundaError;

/** Reaches the engine only through a thrown type and a static call. */
public class ThrowsEngineError {

    public void fail() {
        throw CamundaError.bpmnError("Err_Rejected", "rejected");
    }
}

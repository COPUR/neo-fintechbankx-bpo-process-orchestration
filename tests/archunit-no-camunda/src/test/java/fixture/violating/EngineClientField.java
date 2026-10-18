package fixture.violating;

import io.camunda.client.CamundaClient;

/** Holds the engine client in a field. */
public class EngineClientField {

    private CamundaClient client;

    public CamundaClient client() {
        return client;
    }
}

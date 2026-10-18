package com.bank.bpo.cpt;

import io.camunda.client.annotation.Deployment;
import org.springframework.context.annotation.Configuration;

/** Deploys every model from models/ into the test engine, the way deploy-models does from main. */
@Configuration
@Deployment(resources = {
    "classpath:bpmn/_lib/lib-screening-review.bpmn",
    "classpath:bpmn/acc/acc-tpp-account-opening.bpmn",
    "classpath:dmn/cmp/cmp-review-routing.dmn",
    "classpath:forms/cmp-review-analyst-decision.form",
    "classpath:forms/cmp-review-senior-decision.form"
})
class ModelDeployment {
}

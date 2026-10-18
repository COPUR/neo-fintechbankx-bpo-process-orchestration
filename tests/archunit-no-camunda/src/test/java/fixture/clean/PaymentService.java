package fixture.clean;

/** A domain class with no engine dependency: what the rule must let through. */
public class PaymentService {

    public String confirm(String paymentId) {
        return paymentId + ":confirmed";
    }
}

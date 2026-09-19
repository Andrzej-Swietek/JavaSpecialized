package demo.orders;

/** A message as it would travel through Kafka: optional fields without a single box. */
public record OrderEvent(long id, int quantity, Opt<int> promoCode, Opt<double> discount) {

    public double total(double unitPrice) {
        double gross = quantity * unitPrice;
        return gross - discount.fold(0.0, d -> gross * d);   // OptDouble.fold(Double, DoubleFunction<Double>)
    }
}

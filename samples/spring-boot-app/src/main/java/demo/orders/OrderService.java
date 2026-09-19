package demo.orders;

import dev.specialize.Inline;
import dev.specialize.TailRec;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final PriceList prices;

    @Value
    public static class PriceList {              // Lombok generates the constructor, getter, equals
        double unitPrice;
    }

    public OrderEvent find(long id) {
        Opt<int> promo = id % 2 == 0 ? Opt.some((int) (id * 10)) : Opt.empty();
        Opt<double> discount = promo.filter(code -> code > 100).fold(Opt.<double>empty(), code -> Opt.some(0.1));   // a generic R parameter needs the explicit spelling
        return new OrderEvent(id, 3, promo, discount);
    }

    public double total(long id) {
        return round(find(id).total(prices.getUnitPrice()));
    }

    @Inline
    static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    @TailRec
    static long checksum(List<Long> ids, int i, long acc) {
        return i == ids.size() ? acc : checksum(ids, i + 1, acc * 31 + ids.get(i));
    }
}

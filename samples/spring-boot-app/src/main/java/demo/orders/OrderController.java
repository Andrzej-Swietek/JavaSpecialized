package demo.orders;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orders;

    public record OrderView(long id, int quantity, boolean promo, double total, String storage) {
    }

    @GetMapping("/orders/{id}")
    public OrderView order(@PathVariable long id) {
        OrderEvent event = orders.find(id);
        return new OrderView(id, event.quantity(), event.promoCode().isDefined(), orders.total(id),
                event.promoCode().getClass().getSimpleName());   // "OptInt": the primitive class, at runtime
    }

    /** The message itself over the wire: {@code {"id":42,"quantity":3,"promoCode":420,"discount":0.1}}. */
    @GetMapping("/events/{id}")
    public OrderEvent event(@PathVariable long id) {
        return orders.find(id);
    }

    /** Accepts the same JSON back; a missing or null field is an empty Opt, never a null reference. */
    @PostMapping("/events")
    public String receive(@RequestBody OrderEvent event) {
        return event.promoCode() + "/" + event.discount() + "/" + event.total(10);
    }

    @GetMapping("/orders/checksum")
    public long checksum() {
        return OrderService.checksum(List.of(1L, 2L, 3L), 0, 7);
    }
}

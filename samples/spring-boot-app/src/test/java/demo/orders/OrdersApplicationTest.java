package demo.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrdersApplicationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void endpointUsesThePrimitiveSpecializations() {
        OrderController.OrderView even = rest.getForObject("/orders/42", OrderController.OrderView.class);
        assertEquals("OptInt", even.storage());
        assertTrue(even.promo());
        assertEquals(26.97, even.total());                       // 3 * 9.99 - 10 % : promo 420 > 100

        OrderController.OrderView odd = rest.getForObject("/orders/7", OrderController.OrderView.class);
        assertEquals(false, odd.promo());
        assertEquals(29.97, odd.total());

        assertEquals(7L * 31 * 31 * 31 + 1L * 31 * 31 + 2L * 31 + 3L, rest.getForObject("/orders/checksum", Long.class));
    }

    @Test
    void messagesTravelAsJson() {
        assertEquals("{\"id\":42,\"quantity\":3,\"promoCode\":420,\"discount\":0.1}", rest.getForObject("/events/42", String.class));
        assertEquals("{\"id\":7,\"quantity\":3,\"promoCode\":null,\"discount\":null}", rest.getForObject("/events/7", String.class));
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String reply = rest.postForObject("/events", new org.springframework.http.HttpEntity<>("{\"id\":1,\"quantity\":2,\"discount\":null}", headers), String.class);
        assertEquals("Opt.Empty/Opt.Empty/20.0", reply);
    }

    @Test
    void messagesStorePrimitives() throws Exception {
        Opt<int> promo = Opt.some(5);
        assertEquals(int.class, OptInt.class.getDeclaredField("value").getType());
        assertEquals(OptInt.class, promo.getClass());
        assertEquals(OptInt.class, OrderEvent.class.getRecordComponents()[2].getType());
    }
}

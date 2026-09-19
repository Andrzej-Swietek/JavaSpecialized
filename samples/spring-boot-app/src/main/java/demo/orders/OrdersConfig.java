package demo.orders;

import dev.specialize.jackson.SpecializeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OrdersConfig {
    @Bean
    SpecializeModule specializeModule() {     // Spring Boot registers every Module bean with its ObjectMapper
        return new SpecializeModule();
    }

    @Bean
    OrderService.PriceList priceList() {
        return new OrderService.PriceList(9.99);
    }
}

package com.fryrank.stripe;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Product;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.model.Price;

public class CreatePrice {
    public static void main(String[] args) throws StripeException {
        // Get API key from environment variable
        // Set it before running: export STRIPE_API_KEY="sk_test_..."
        String apiKey = System.getenv("STRIPE_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("STRIPE_API_KEY environment variable is not set. " +
                    "Please set it before running: export STRIPE_API_KEY=\"sk_test_...\"");
        }
        Stripe.apiKey = apiKey;


        ProductCreateParams productParams =
                ProductCreateParams.builder()
                        .setName("Starter Subscription")
                        .setDescription("$12/Month subscription")
                        .build();

        //https://github.com/stripe/stripe-java/blob/master/src/main/java/com/stripe/model/Product.java
        Product product = Product.create(productParams);
        System.out.println("Success! Here is your starter subscription product id: " + product.getId());

        PriceCreateParams params =
                PriceCreateParams
                        .builder()
                        .setProduct(product.getId())
                        .setCurrency("usd")
                        .setUnitAmount(1200L)
                        .build();
        Price price = Price.create(params);
        System.out.println("Success! Here is your starter subscription price id: " + price.getId());
    }
}
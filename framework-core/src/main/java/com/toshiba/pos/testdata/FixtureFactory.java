// framework-core/src/main/java/com/toshiba/pos/testdata/FixtureFactory.java

package com.toshiba.pos.testdata;

import com.github.javafaker.Faker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FixtureFactory — Config-driven synthetic test data generator.
 * 
 * <p>This factory generates repeatable test fixtures for POS testing,
 * including baskets, items, customers, and transactions.
 * 
 * <p>Uses Java Faker for realistic data generation with a fixed seed
 * for repeatability across test runs.
 * 
 * <p>Example:
 * <pre>
 *   FixtureFactory factory = new FixtureFactory(
 *       new TestDataConfig.Builder().seed(42).build()
 *   );
 *   
 *   List<BasketItem> basket = factory.generateBasket(5);
 *   Transaction transaction = factory.generateTransaction();
 * </pre>
 */
public class FixtureFactory {

    private static final Logger logger = LogManager.getLogger(FixtureFactory.class);

    private final TestDataConfig config;
    private final Faker faker;
    private final Random random;

    // Default product catalog for fixture generation
    private static final List<CatalogItem> DEFAULT_CATALOG = Arrays.asList(
        new CatalogItem("SKU-1001", "Milk (1 gal)", 2.99, 3.78),
        new CatalogItem("SKU-1002", "Bread (white)", 1.49, 0.45),
        new CatalogItem("SKU-1003", "Eggs (dozen)", 3.99, 0.68),
        new CatalogItem("SKU-1004", "Chicken Breast (lb)", 4.50, 0.45),
        new CatalogItem("SKU-1005", "Apple (each)", 0.99, 0.18),
        new CatalogItem("SKU-1006", "Orange Juice (64oz)", 1.99, 1.89),
        new CatalogItem("SKU-1007", "Cheese (cheddar, 8oz)", 5.49, 0.23),
        new CatalogItem("SKU-1008", "Butter (salted, 1lb)", 2.29, 0.45),
        new CatalogItem("SKU-1009", "Cereal (family size)", 3.29, 0.91),
        new CatalogItem("SKU-1010", "Coffee (ground, 12oz)", 7.99, 0.34)
    );

    // Regions and their tax rates
    private static final List<Region> REGIONS = Arrays.asList(
        new Region("CA", "California", 0.0725),
        new Region("TX", "Texas", 0.0625),
        new Region("NY", "New York", 0.04),
        new Region("FL", "Florida", 0.06),
        new Region("OR", "Oregon", 0.0),
        new Region("WA", "Washington", 0.065),
        new Region("IL", "Illinois", 0.0625),
        new Region("PA", "Pennsylvania", 0.06)
    );

    // Test card sentinels
    private static final List<String> TEST_CARDS = Arrays.asList(
        "4111111111111111",  // Approved
        "4111111111110000",  // Declined
        "4111111111111111",  // Approved with warning (same as approved for simplicity)
        "4111111111112222"   // Approved (additional test card)
    );

    // Payment methods
    private static final List<String> PAYMENT_METHODS = Arrays.asList(
        "card", "nfc", "biometric", "cash", "gift_card"
    );

    /**
     * Create a FixtureFactory with the given configuration.
     */
    public FixtureFactory(TestDataConfig config) {
        this.config = config;
        this.faker = new Faker(new Locale(config.getLocale()));
        this.faker.seed(config.getSeed());
        this.random = new Random(config.getSeed());
        logger.info("FixtureFactory initialized with seed: {}, locale: {}", config.getSeed(), config.getLocale());
    }

    /**
     * Create a FixtureFactory with default configuration.
     */
    public static FixtureFactory defaultFactory() {
        return new FixtureFactory(new TestDataConfig.Builder().build());
    }

    /**
     * Generate a random basket of items.
     * 
     * @param size Number of items in the basket
     * @return List of BasketItem objects
     */
    public List<BasketItem> generateBasket(int size) {
        List<BasketItem> basket = new ArrayList<>();
        List<CatalogItem> catalogCopy = new ArrayList<>(DEFAULT_CATALOG);
        
        for (int i = 0; i < size; i++) {
            CatalogItem catalogItem = catalogCopy.get(random.nextInt(catalogCopy.size()));
            int quantity = random.nextInt(3) + 1;  // 1-3 quantity
            
            BasketItem item = new BasketItem(
                catalogItem.getSku(),
                catalogItem.getName(),
                catalogItem.getPrice(),
                quantity,
                catalogItem.getWeightKg()
            );
            basket.add(item);
        }
        
        logger.debug("Generated basket with {} items", basket.size());
        return basket;
    }

    /**
     * Generate a basket with a specific set of SKUs.
     */
    public List<BasketItem> generateBasket(List<String> skus) {
        List<BasketItem> basket = new ArrayList<>();
        for (String sku : skus) {
            Optional<CatalogItem> catalogItem = DEFAULT_CATALOG.stream()
                .filter(item -> item.getSku().equals(sku))
                .findFirst();
            
            if (catalogItem.isPresent()) {
                CatalogItem item = catalogItem.get();
                int quantity = random.nextInt(2) + 1;
                basket.add(new BasketItem(
                    item.getSku(),
                    item.getName(),
                    item.getPrice(),
                    quantity,
                    item.getWeightKg()
                ));
            }
        }
        return basket;
    }

    /**
     * Generate a random basket with default size.
     */
    public List<BasketItem> generateBasket() {
        return generateBasket(config.getDefaultBasketSize());
    }

    /**
     * Generate a random region.
     */
    public Region generateRegion() {
        return REGIONS.get(random.nextInt(REGIONS.size()));
    }

    /**
     * Generate a random test card number.
     */
    public String generateTestCard() {
        return TEST_CARDS.get(random.nextInt(TEST_CARDS.size()));
    }

    /**
     * Generate a random payment method.
     */
    public String generatePaymentMethod() {
        return PAYMENT_METHODS.get(random.nextInt(PAYMENT_METHODS.size()));
    }

     /**
     * Generate a standard test basket with a known set of SKUs.
     * Used for consistent testing across all test classes.
     */
    public List<BasketItem> getStandardTestBasket() {
        return generateBasket(Arrays.asList("SKU-1001", "SKU-1002", "SKU-1005"));
    }

    /**
     * Get a standard SKU for testing.
     */
    public String getStandardSku() {
        return "SKU-1001";
    }

    /**
     * Get a standard region for testing.
     */
    public String getStandardRegion() {
        return "CA";
    }

    /**
     * Get a standard test card number.
     */
    public String getStandardCard() {
        return "4111111111111111";
    }

    /**
     * Get a declined test card number.
     */
    public String getDeclinedCard() {
        return "4111111111110000";
    }

    /**
     * Get a standard customer ID for testing.
     */
    public String getStandardCustomerId() {
        return "CUST-TEST-001";
    }

    /**
     * Generate a standard product fixture for a specific product.
     */
    public ProductFixture getStandardProductFixture(String productId) {
        return generateProductFixture(productId);
    }

    /**
     * Generate a random customer.
     */
    public Customer generateCustomer() {
        String firstName = faker.name().firstName();
        String lastName = faker.name().lastName();
        String email = faker.internet().emailAddress(firstName.toLowerCase() + "." + lastName.toLowerCase());
        String phone = faker.phoneNumber().phoneNumber();
        
        return new Customer(firstName, lastName, email, phone);
    }

    /**
     * Generate a complete transaction fixture.
     */
    public Transaction generateTransaction() {
        List<BasketItem> basket = generateBasket();
        Region region = generateRegion();
        String card = generateTestCard();
        Customer customer = generateCustomer();
        
        double subtotal = basket.stream()
            .mapToDouble(item -> item.getPrice() * item.getQuantity())
            .sum();
        double tax = subtotal * region.getTaxRate();
        double total = subtotal + tax;
        
        String transactionId = "TXN-" + System.currentTimeMillis() + "-" + (random.nextInt(10000) + 1000);
        
        return new Transaction(
            transactionId,
            basket,
            region,
            card,
            customer,
            subtotal,
            tax,
            total,
            new Date()
        );
    }

    /**
     * Generate a transaction with specific items and region.
     */
    public Transaction generateTransaction(List<String> skus, String regionCode) {
        List<BasketItem> basket = generateBasket(skus);
        
        Optional<Region> regionOpt = REGIONS.stream()
            .filter(r -> r.getCode().equals(regionCode))
            .findFirst();
        
        Region region = regionOpt.orElse(REGIONS.get(0));
        String card = generateTestCard();
        Customer customer = generateCustomer();
        
        double subtotal = basket.stream()
            .mapToDouble(item -> item.getPrice() * item.getQuantity())
            .sum();
        double tax = subtotal * region.getTaxRate();
        double total = subtotal + tax;
        
        String transactionId = "TXN-" + System.currentTimeMillis() + "-" + (random.nextInt(10000) + 1000);
        
        return new Transaction(
            transactionId,
            basket,
            region,
            card,
            customer,
            subtotal,
            tax,
            total,
            new Date()
        );
    }

    /**
     * Generate a fixture for a specific product adapter.
     * 
     * <p>This method respects product-specific overrides from the config.
     */
    public ProductFixture generateProductFixture(String productId) {
        List<BasketItem> basket = generateBasket();
        Region region = generateRegion();
        String card = generateTestCard();
        
        // Check for overrides
        if (config.hasOverride(productId + ".basketSize")) {
            int size = config.getOverride(productId + ".basketSize", Integer.class);
            basket = generateBasket(size);
        }
        if (config.hasOverride(productId + ".region")) {
            String regionCode = config.getOverride(productId + ".region", String.class);
            Optional<Region> regionOpt = REGIONS.stream()
                .filter(r -> r.getCode().equals(regionCode))
                .findFirst();
            region = regionOpt.orElse(region);
        }
        if (config.hasOverride(productId + ".card")) {
            card = config.getOverride(productId + ".card", String.class);
        }
        
        double subtotal = basket.stream()
            .mapToDouble(item -> item.getPrice() * item.getQuantity())
            .sum();
        double tax = subtotal * region.getTaxRate();
        double total = subtotal + tax;
        
        return new ProductFixture(
            productId,
            basket,
            region,
            card,
            subtotal,
            tax,
            total
        );
    }

    /**
     * Get the catalog item for a SKU.
     */
    public Optional<CatalogItem> getCatalogItem(String sku) {
        return DEFAULT_CATALOG.stream()
            .filter(item -> item.getSku().equals(sku))
            .findFirst();
    }

    /**
     * Get the full catalog.
     */
    public List<CatalogItem> getCatalog() {
        return new ArrayList<>(DEFAULT_CATALOG);
    }

    /**
     * Get all available regions.
     */
    public List<Region> getRegions() {
        return new ArrayList<>(REGIONS);
    }

    /**
     * Get all test cards.
     */
    public List<String> getTestCards() {
        return new ArrayList<>(TEST_CARDS);
    }

    // ============================================================
    // Inner model classes
    // ============================================================

    public static class CatalogItem {
        private final String sku;
        private final String name;
        private final double price;
        private final double weightKg;

        public CatalogItem(String sku, String name, double price, double weightKg) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.weightKg = weightKg;
        }

        public String getSku() { return sku; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public double getWeightKg() { return weightKg; }

        @Override
        public String toString() {
            return "CatalogItem{sku='" + sku + "', name='" + name + "', price=" + price + "}";
        }
    }

    public static class BasketItem {
        private final String sku;
        private final String name;
        private final double price;
        private final int quantity;
        private final double weightKg;

        public BasketItem(String sku, String name, double price, int quantity, double weightKg) {
            this.sku = sku;
            this.name = name;
            this.price = price;
            this.quantity = quantity;
            this.weightKg = weightKg;
        }

        public String getSku() { return sku; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public int getQuantity() { return quantity; }
        public double getWeightKg() { return weightKg; }
        public double getTotal() { return price * quantity; }

        @Override
        public String toString() {
            return "BasketItem{sku='" + sku + "', name='" + name + "', quantity=" + quantity + ", total=" + getTotal() + "}";
        }
    }

    public static class Region {
        private final String code;
        private final String name;
        private final double taxRate;

        public Region(String code, String name, double taxRate) {
            this.code = code;
            this.name = name;
            this.taxRate = taxRate;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public double getTaxRate() { return taxRate; }

        @Override
        public String toString() {
            return "Region{code='" + code + "', name='" + name + "', taxRate=" + taxRate + "}";
        }
    }

    public static class Customer {
        private final String firstName;
        private final String lastName;
        private final String email;
        private final String phone;

        public Customer(String firstName, String lastName, String email, String phone) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.phone = phone;
        }

        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getFullName() { return firstName + " " + lastName; }

        @Override
        public String toString() {
            return "Customer{name='" + getFullName() + "', email='" + email + "'}";
        }
    }

    public static class Transaction {
        private final String transactionId;
        private final List<BasketItem> basket;
        private final Region region;
        private final String cardNumber;
        private final Customer customer;
        private final double subtotal;
        private final double tax;
        private final double total;
        private final Date timestamp;

        public Transaction(String transactionId, List<BasketItem> basket, Region region,
                          String cardNumber, Customer customer, double subtotal,
                          double tax, double total, Date timestamp) {
            this.transactionId = transactionId;
            this.basket = basket;
            this.region = region;
            this.cardNumber = cardNumber;
            this.customer = customer;
            this.subtotal = subtotal;
            this.tax = tax;
            this.total = total;
            this.timestamp = timestamp;
        }

        public String getTransactionId() { return transactionId; }
        public List<BasketItem> getBasket() { return basket; }
        public Region getRegion() { return region; }
        public String getCardNumber() { return cardNumber; }
        public Customer getCustomer() { return customer; }
        public double getSubtotal() { return subtotal; }
        public double getTax() { return tax; }
        public double getTotal() { return total; }
        public Date getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return "Transaction{id='" + transactionId + "', items=" + basket.size() + ", total=" + total + "}";
        }
    }

    public static class ProductFixture {
        private final String productId;
        private final List<BasketItem> basket;
        private final Region region;
        private final String cardNumber;
        private final double subtotal;
        private final double tax;
        private final double total;

        public ProductFixture(String productId, List<BasketItem> basket, Region region,
                             String cardNumber, double subtotal, double tax, double total) {
            this.productId = productId;
            this.basket = basket;
            this.region = region;
            this.cardNumber = cardNumber;
            this.subtotal = subtotal;
            this.tax = tax;
            this.total = total;
        }

        public String getProductId() { return productId; }
        public List<BasketItem> getBasket() { return basket; }
        public Region getRegion() { return region; }
        public String getCardNumber() { return cardNumber; }
        public double getSubtotal() { return subtotal; }
        public double getTax() { return tax; }
        public double getTotal() { return total; }
    }
}
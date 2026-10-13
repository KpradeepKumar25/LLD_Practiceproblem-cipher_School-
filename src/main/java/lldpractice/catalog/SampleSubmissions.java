package lldpractice.catalog;

import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;

/**
 * Hand-written sample submissions used both for the "prefill" demo links in the web UI and for
 * tests, across two problems. {@link #strongStrategyBased()} and {@link #strongRecordBased()}
 * use deliberately different vocabulary and structure (interfaces vs. an abstract class +
 * records) to prove the platform rewards more than one valid design, not a single model answer;
 * {@link #vendingWeak()} and {@link #vendingStrong()} repeat that same weak/strong contrast for
 * a second problem so the evaluator's behaviour isn't shown on only one.
 */
public final class SampleSubmissions {

    private SampleSubmissions() {
    }

    public static Submission weak() {
        String code = """
                import java.util.ArrayList;
                import java.util.List;

                public class ParkingLot {
                    public List<Object> spots = new ArrayList<>();
                    public List<Object> vehicles = new ArrayList<>();
                    public int capacity;
                    public double totalRevenue;
                    public String name;

                    public void addSpot(Object spot) {
                        spots.add(spot);
                    }

                    public boolean parkVehicle(Object vehicle, int hours) {
                        if (vehicle instanceof Car) {
                            System.out.println("car parking");
                        } else if (vehicle instanceof Bike) {
                            System.out.println("bike parking");
                        } else if (vehicle instanceof Truck) {
                            System.out.println("truck parking");
                        }
                        for (Object s : spots) {
                            vehicles.add(vehicle);
                            totalRevenue += calculateFee(vehicle, hours);
                            return true;
                        }
                        return false;
                    }

                    public double calculateFee(Object vehicle, int hours) {
                        if (vehicle instanceof Car) {
                            return hours * 2.0;
                        } else if (vehicle instanceof Bike) {
                            return hours * 1.0;
                        } else if (vehicle instanceof Truck) {
                            return hours * 3.0;
                        }
                        return 0;
                    }

                    public void removeVehicle(Object vehicle) {
                        vehicles.remove(vehicle);
                    }

                    public int getFreeSpots() {
                        return capacity - vehicles.size();
                    }

                    public void printReport() {
                        System.out.println("Revenue: " + totalRevenue);
                    }

                    public void resetRevenue() {
                        totalRevenue = 0;
                    }
                }

                class Car {}
                class Bike {}
                class Truck {}
                """;
        return new Submission(
                "none really",
                "One ParkingLot class does everything: spots, vehicles, pricing, reporting.",
                code,
                "",
                SubmissionFormat.JAVA);
    }

    public static Submission strongStrategyBased() {
        String code = """
                import java.util.ArrayList;
                import java.util.List;
                import java.util.Optional;

                interface Vehicle {
                    String getLicensePlate();
                    VehicleSize getSize();
                }

                enum VehicleSize {
                    SMALL, MEDIUM, LARGE
                }

                final class Car implements Vehicle {
                    private final String licensePlate;
                    Car(String licensePlate) { this.licensePlate = licensePlate; }
                    public String getLicensePlate() { return licensePlate; }
                    public VehicleSize getSize() { return VehicleSize.MEDIUM; }
                }

                final class Bike implements Vehicle {
                    private final String licensePlate;
                    Bike(String licensePlate) { this.licensePlate = licensePlate; }
                    public String getLicensePlate() { return licensePlate; }
                    public VehicleSize getSize() { return VehicleSize.SMALL; }
                }

                final class ParkingSpot {
                    private final String id;
                    private final VehicleSize size;
                    private Vehicle occupiedBy;

                    ParkingSpot(String id, VehicleSize size) {
                        this.id = id;
                        this.size = size;
                    }

                    boolean isFree() {
                        return occupiedBy == null;
                    }

                    boolean fits(Vehicle vehicle) {
                        return size == vehicle.getSize();
                    }

                    void occupy(Vehicle vehicle) {
                        this.occupiedBy = vehicle;
                    }

                    void vacate() {
                        this.occupiedBy = null;
                    }

                    String getId() {
                        return id;
                    }
                }

                interface SpotAllocator {
                    Optional<ParkingSpot> allocate(List<ParkingSpot> spots, Vehicle vehicle);
                }

                final class FirstFitSpotAllocator implements SpotAllocator {
                    public Optional<ParkingSpot> allocate(List<ParkingSpot> spots, Vehicle vehicle) {
                        for (ParkingSpot spot : spots) {
                            if (spot.isFree() && spot.fits(vehicle)) {
                                return Optional.of(spot);
                            }
                        }
                        return Optional.empty();
                    }
                }

                interface PricingStrategy {
                    double calculateFee(Vehicle vehicle, long minutesParked);
                }

                final class HourlyPricingStrategy implements PricingStrategy {
                    private final double ratePerHour;
                    HourlyPricingStrategy(double ratePerHour) { this.ratePerHour = ratePerHour; }
                    public double calculateFee(Vehicle vehicle, long minutesParked) {
                        long hours = Math.max(1, (minutesParked + 59) / 60);
                        return hours * ratePerHour;
                    }
                }

                final class ParkingTicket {
                    private final String ticketId;
                    private final Vehicle vehicle;
                    private final ParkingSpot spot;
                    private final long entryTimeMinutes;

                    ParkingTicket(String ticketId, Vehicle vehicle, ParkingSpot spot, long entryTimeMinutes) {
                        this.ticketId = ticketId;
                        this.vehicle = vehicle;
                        this.spot = spot;
                        this.entryTimeMinutes = entryTimeMinutes;
                    }

                    String getTicketId() { return ticketId; }
                    Vehicle getVehicle() { return vehicle; }
                    ParkingSpot getSpot() { return spot; }
                    long getEntryTimeMinutes() { return entryTimeMinutes; }
                }

                public final class ParkingLot {
                    private final List<ParkingSpot> spots = new ArrayList<>();
                    private final SpotAllocator allocator;
                    private final PricingStrategy pricingStrategy;

                    public ParkingLot(SpotAllocator allocator, PricingStrategy pricingStrategy) {
                        this.allocator = allocator;
                        this.pricingStrategy = pricingStrategy;
                    }

                    public void addSpot(ParkingSpot spot) {
                        spots.add(spot);
                    }

                    public Optional<ParkingTicket> park(Vehicle vehicle, long nowMinutes) {
                        Optional<ParkingSpot> spot = allocator.allocate(spots, vehicle);
                        if (spot.isEmpty()) {
                            return Optional.empty();
                        }
                        spot.get().occupy(vehicle);
                        return Optional.of(new ParkingTicket(spot.get().getId() + "-" + nowMinutes, vehicle, spot.get(), nowMinutes));
                    }

                    public double exit(ParkingTicket ticket, long nowMinutes) {
                        double fee = pricingStrategy.calculateFee(ticket.getVehicle(), nowMinutes - ticket.getEntryTimeMinutes());
                        ticket.getSpot().vacate();
                        return fee;
                    }

                    public long freeSpotCount() {
                        return spots.stream().filter(ParkingSpot::isFree).count();
                    }
                }
                """;
        String assumptions = "Each spot fits exactly one vehicle size class. Pricing is per started hour. "
                + "A ticket is issued only if a spot was actually found; exit always has a matching ticket.";
        String outline = "Vehicle (interface) <- Car, Bike. ParkingSpot holds its own occupancy. "
                + "SpotAllocator picks a spot for a vehicle (FirstFitSpotAllocator is one policy). "
                + "PricingStrategy computes the fee (HourlyPricingStrategy is one policy). "
                + "ParkingTicket ties a vehicle, spot and entry time together. ParkingLot wires these together "
                + "and exposes park()/exit()/freeSpotCount().";
        String tradeOffs = "Chose Strategy interfaces for allocation and pricing so a new policy (e.g. nearest-spot, "
                + "flat-rate) is a new class, not a change to ParkingLot. Trade-off: more small classes/files up front, "
                + "but ParkingLot itself never needs to change when a policy changes. Used Optional instead of null "
                + "to make 'no spot available' explicit at the call site.";
        return new Submission(assumptions, outline, code, tradeOffs, SubmissionFormat.JAVA);
    }

    public static Submission strongRecordBased() {
        String code = """
                import java.util.ArrayList;
                import java.util.List;
                import java.util.Optional;

                abstract class Vehicle {
                    private final String plate;
                    protected Vehicle(String plate) { this.plate = plate; }
                    public String plate() { return plate; }
                    public abstract int sizeUnits();
                }

                final class Car extends Vehicle {
                    Car(String plate) { super(plate); }
                    public int sizeUnits() { return 2; }
                }

                final class Bike extends Vehicle {
                    Bike(String plate) { super(plate); }
                    public int sizeUnits() { return 1; }
                }

                record Bay(String id, int capacityUnits, boolean occupied) {
                    Bay occupy() { return new Bay(id, capacityUnits, true); }
                    Bay free() { return new Bay(id, capacityUnits, false); }
                    boolean fits(Vehicle vehicle) { return !occupied && vehicle.sizeUnits() <= capacityUnits; }
                }

                interface AllocationStrategy {
                    Optional<Bay> pick(List<Bay> bays, Vehicle vehicle);
                }

                final class FirstFitAllocation implements AllocationStrategy {
                    public Optional<Bay> pick(List<Bay> bays, Vehicle vehicle) {
                        return bays.stream().filter(b -> b.fits(vehicle)).findFirst();
                    }
                }

                interface FeeStrategy {
                    double feeFor(Vehicle vehicle, long minutes);
                }

                final class FlatRateFeeStrategy implements FeeStrategy {
                    private final double ratePerHour;
                    FlatRateFeeStrategy(double ratePerHour) { this.ratePerHour = ratePerHour; }
                    public double feeFor(Vehicle vehicle, long minutes) {
                        long hours = Math.max(1, (minutes + 59) / 60);
                        return hours * ratePerHour;
                    }
                }

                record Receipt(String id, Vehicle vehicle, Bay bay, long entryMinutes) {
                }

                public final class ParkingLot {
                    private final List<Bay> bays = new ArrayList<>();
                    private final AllocationStrategy allocationStrategy;
                    private final FeeStrategy feeStrategy;

                    public ParkingLot(AllocationStrategy allocationStrategy, FeeStrategy feeStrategy) {
                        this.allocationStrategy = allocationStrategy;
                        this.feeStrategy = feeStrategy;
                    }

                    public void addBay(Bay bay) {
                        bays.add(bay);
                    }

                    public Optional<Receipt> park(Vehicle vehicle, long nowMinutes) {
                        Optional<Bay> bay = allocationStrategy.pick(bays, vehicle);
                        if (bay.isEmpty()) {
                            return Optional.empty();
                        }
                        bays.remove(bay.get());
                        bays.add(bay.get().occupy());
                        return Optional.of(new Receipt(bay.get().id() + "-" + nowMinutes, vehicle, bay.get(), nowMinutes));
                    }

                    public double exit(Receipt receipt, long nowMinutes) {
                        double fee = feeStrategy.feeFor(receipt.vehicle(), nowMinutes - receipt.entryMinutes());
                        bays.remove(receipt.bay());
                        bays.add(receipt.bay().free());
                        return fee;
                    }

                    public long freeBayCount() {
                        return bays.stream().filter(b -> !b.occupied()).count();
                    }
                }
                """;
        String assumptions = "A bay has a fixed capacity in abstract 'size units'; a vehicle fits if its size is "
                + "within the bay's capacity. Fee is per started hour, same rate for every vehicle in this version.";
        String outline = "Vehicle (abstract class) <- Car, Bike. Bay is an immutable record representing one spot's "
                + "state (occupied or not) rather than a mutable object. AllocationStrategy picks a bay "
                + "(FirstFitAllocation is one policy). FeeStrategy computes the charge (FlatRateFeeStrategy is one "
                + "policy). Receipt is an immutable record of one parking session. ParkingLot coordinates all of it.";
        String tradeOffs = "Modelled Bay and Receipt as records instead of mutable classes: occupying a bay returns "
                + "a new Bay rather than mutating one, which makes state changes explicit at the call site at the "
                + "cost of replacing entries in the list on every park()/exit(). Used an abstract class for Vehicle "
                + "instead of an interface since plate storage is shared, identical logic across vehicle types.";
        return new Submission(assumptions, outline, code, tradeOffs, SubmissionFormat.JAVA);
    }

    /** A deliberately weak Vending Machine submission: one class doing everything, public
     * mutable fields, and an instanceof chain for payment type instead of a shared abstraction. */
    public static Submission vendingWeak() {
        String code = """
                import java.util.ArrayList;
                import java.util.List;

                public class VendingMachine {
                    public List<Object> items = new ArrayList<>();
                    public List<Object> payments = new ArrayList<>();
                    public double balance;
                    public int stock;
                    public String status;

                    public void addItem(Object item) {
                        items.add(item);
                    }

                    public boolean pay(Object payment, double amount) {
                        if (payment instanceof Coin) {
                            balance += amount;
                        } else if (payment instanceof Card) {
                            balance += amount;
                        } else if (payment instanceof Note) {
                            balance += amount;
                        }
                        return true;
                    }

                    public Object dispense(int slot) {
                        if (stock <= 0) {
                            return null;
                        }
                        stock--;
                        return items.get(slot);
                    }

                    public void refund() {
                        balance = 0;
                    }

                    public void setStatus(String s) {
                        status = s;
                    }

                    public double getBalance() {
                        return balance;
                    }

                    public void restock(int n) {
                        stock += n;
                    }
                }

                class Coin {}
                class Card {}
                class Note {}
                """;
        return new Submission(
                "none really",
                "One VendingMachine class handles items, payments, balance and stock.",
                code,
                "",
                SubmissionFormat.JAVA);
    }

    /** A strong Vending Machine submission using a Payment strategy interface and a dedicated
     * Inventory/Slot model, deliberately different vocabulary/structure from the weak sample. */
    public static Submission vendingStrong() {
        String code = """
                import java.util.ArrayList;
                import java.util.HashMap;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;

                interface PaymentMethod {
                    boolean accept(double amountOwed);
                }

                final class CoinPayment implements PaymentMethod {
                    private final double inserted;
                    CoinPayment(double inserted) { this.inserted = inserted; }
                    public boolean accept(double amountOwed) { return inserted >= amountOwed; }
                }

                final class CardPayment implements PaymentMethod {
                    private final boolean approved;
                    CardPayment(boolean approved) { this.approved = approved; }
                    public boolean accept(double amountOwed) { return approved; }
                }

                final class Product {
                    private final String name;
                    private final double price;
                    Product(String name, double price) { this.name = name; this.price = price; }
                    String getName() { return name; }
                    double getPrice() { return price; }
                }

                final class Slot {
                    private final String code;
                    private final Product product;
                    private int quantity;

                    Slot(String code, Product product, int quantity) {
                        this.code = code;
                        this.product = product;
                        this.quantity = quantity;
                    }

                    boolean inStock() {
                        return quantity > 0;
                    }

                    void dispenseOne() {
                        quantity--;
                    }

                    String getCode() { return code; }
                    Product getProduct() { return product; }
                    int getQuantity() { return quantity; }
                }

                final class Inventory {
                    private final Map<String, Slot> slots = new HashMap<>();

                    void addSlot(Slot slot) {
                        slots.put(slot.getCode(), slot);
                    }

                    Optional<Slot> find(String code) {
                        return Optional.ofNullable(slots.get(code));
                    }

                    List<Slot> all() {
                        return new ArrayList<>(slots.values());
                    }
                }

                public final class VendingMachine {
                    private final Inventory inventory;

                    public VendingMachine(Inventory inventory) {
                        this.inventory = inventory;
                    }

                    public Optional<Product> purchase(String slotCode, PaymentMethod payment) {
                        Optional<Slot> slot = inventory.find(slotCode);
                        if (slot.isEmpty() || !slot.get().inStock()) {
                            return Optional.empty();
                        }
                        Product product = slot.get().getProduct();
                        if (!payment.accept(product.getPrice())) {
                            return Optional.empty();
                        }
                        slot.get().dispenseOne();
                        return Optional.of(product);
                    }

                    public List<Slot> listSlots() {
                        return inventory.all();
                    }
                }
                """;
        String assumptions = "Each slot holds one product and a quantity. A payment is only ever checked once, "
                + "against the product's price, at purchase time -- no partial payments or change-making modelled.";
        String outline = "PaymentMethod (interface) <- CoinPayment, CardPayment, each deciding for itself whether "
                + "the amount owed is covered. Product is a plain price-carrying value. Slot pairs a Product with "
                + "remaining quantity. Inventory holds all slots by code. VendingMachine coordinates purchase() and "
                + "exposes listSlots() for display.";
        String tradeOffs = "Chose a PaymentMethod interface so a new payment type (e.g. mobile wallet) is a new "
                + "class, not a change to VendingMachine or Slot. Trade-off: VendingMachine doesn't know or care how "
                + "payment was validated, which also means it can't itself enforce cross-payment-method business "
                + "rules (e.g. a minimum card amount) without each implementation duplicating that check.";
        return new Submission(assumptions, outline, code, tradeOffs, SubmissionFormat.JAVA);
    }
}

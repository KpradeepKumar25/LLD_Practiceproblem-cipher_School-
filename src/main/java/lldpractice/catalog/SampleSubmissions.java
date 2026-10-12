package lldpractice.catalog;

import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;

/**
 * Hand-written Parking Lot submissions used both for the "prefill" demo links in the web UI and
 * for tests. {@link #strongStrategyBased()} and {@link #strongRecordBased()} use deliberately
 * different vocabulary and structure (interfaces vs. an abstract class + records) to prove the
 * platform rewards more than one valid design, not a single model answer.
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
}

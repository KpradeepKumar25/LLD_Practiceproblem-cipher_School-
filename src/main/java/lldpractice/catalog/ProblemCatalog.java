package lldpractice.catalog;

import java.util.List;

import lldpractice.domain.Concept;
import lldpractice.domain.Difficulty;
import lldpractice.domain.Problem;
import lldpractice.domain.Rubric;

/**
 * The three LLD problems the platform ships with. Content lives here, hand-written, rather than
 * being user-submitted, so the expected {@link Concept}s can be curated to genuinely accept
 * multiple valid namings (see {@link Concept}'s Javadoc) instead of anchoring on one model answer.
 */
public final class ProblemCatalog {

    private ProblemCatalog() {
    }

    public static List<Problem> all() {
        return List.of(parkingLot(), elevatorSystem(), vendingMachine());
    }

    public static Problem parkingLot() {
        return new Problem(
                "parking-lot",
                "Parking Lot",
                Difficulty.MEDIUM,
                "Design a parking lot that can assign incoming vehicles to free spots, issue a "
                        + "ticket, and charge a fee on exit, with room to support new pricing schemes later.",
                List.of(
                        "Support multiple vehicle types (e.g. car, bike, truck) with different spot size needs.",
                        "Track available and occupied spots, potentially across multiple levels.",
                        "Assign an incoming vehicle to a suitable free spot and issue a ticket.",
                        "Calculate the parking fee on exit, based on duration and vehicle type.",
                        "Support adding a new pricing scheme (e.g. hourly vs. flat-rate) without rewriting core logic.",
                        "Prevent two vehicles from being assigned to the same spot.",
                        "Report the number of free spots per level or type at any time."
                ),
                List.of(
                        "What varies independently here: vehicle type, spot type, or pricing? Should each vary without touching the others?",
                        "Who decides which spot a vehicle gets? Is that decision reusable for a new allocation rule later?",
                        "What happens at the boundary cases (lot is full, invalid ticket on exit)? Who is responsible for handling that?"
                ),
                List.of(
                        "Consider separating 'where does this vehicle fit' (allocation) from 'how much does it cost' (pricing) into different interfaces.",
                        "A Ticket capturing entry time, spot and vehicle is often cleaner than threading three separate values through method calls.",
                        "A small VehicleType/SpotType enum (or sealed hierarchy) keeps size-matching logic in one place."
                ),
                Rubric.standard(),
                List.of(
                        new Concept("a parking spot/slot abstraction", List.of("Spot", "Slot", "Bay"), true),
                        new Concept("a vehicle abstraction", List.of("Vehicle"), true),
                        new Concept("a parking ticket/receipt", List.of("Ticket", "Receipt"), true),
                        new Concept("the parking lot/garage container", List.of("ParkingLot", "Garage"), true),
                        new Concept("a pluggable pricing/fee strategy", List.of("Pricing", "FeeCalculator", "RateCalculator", "PricingStrategy", "FeeStrategy"), false),
                        new Concept("a pluggable spot allocation strategy", List.of("Allocator", "AllocationStrategy", "ParkingStrategy", "SpotFinder", "SpotAssigner"), false)
                )
        );
    }

    public static Problem elevatorSystem() {
        return new Problem(
                "elevator-system",
                "Elevator System",
                Difficulty.HARD,
                "Design a multi-elevator system that can accept hall calls and cabin requests, "
                        + "decide which elevator serves each one, and track each elevator's state safely.",
                List.of(
                        "Support multiple elevators serving the same set of floors.",
                        "Handle external hall calls (floor + direction) and internal cabin requests (destination floor).",
                        "Decide which elevator should serve a new request (dispatch/scheduling).",
                        "Track each elevator's current floor, direction and door state.",
                        "Support adding a new dispatch strategy later (e.g. nearest-car vs. zone-based) without rewriting the elevators.",
                        "Only open or close doors while the elevator is stopped at a floor.",
                        "Handle one elevator going out of service without breaking the whole system."
                ),
                List.of(
                        "Should an elevator know how requests get assigned to it, or should something else decide?",
                        "What's the difference between a hall call and a cabin request in your model?",
                        "How would you add a priority request (e.g. fire mode) without touching existing dispatch logic?"
                ),
                List.of(
                        "A Scheduler/Dispatcher interface separates 'which elevator handles this' from the elevator's own state machine.",
                        "Model elevator direction/state explicitly (e.g. IDLE, MOVING_UP, MOVING_DOWN, DOORS_OPEN) rather than booleans.",
                        "Hall calls and cabin requests often deserve different small classes even though both are 'requests'."
                ),
                Rubric.standard(),
                List.of(
                        new Concept("an elevator/car abstraction", List.of("Elevator", "ElevatorCar", "Car", "Lift"), true),
                        new Concept("a request/call abstraction", List.of("Request", "Call", "HallCall", "CabinRequest"), true),
                        new Concept("a pluggable dispatch/scheduling strategy", List.of("Dispatcher", "Scheduler", "SchedulingStrategy", "DispatchStrategy"), true),
                        new Concept("explicit elevator direction/state", List.of("Direction", "ElevatorState", "State"), false),
                        new Concept("the overall controlling system", List.of("ElevatorSystem", "ElevatorController", "Controller"), false),
                        new Concept("a door abstraction", List.of("Door"), false)
                )
        );
    }

    public static Problem vendingMachine() {
        return new Problem(
                "vending-machine",
                "Vending Machine",
                Difficulty.MEDIUM,
                "Design a vending machine that tracks inventory per slot, accepts more than one "
                        + "payment method, and dispenses products safely.",
                List.of(
                        "Display available products and their prices.",
                        "Accept more than one payment method (e.g. coins and card) without the core flow caring which one was used.",
                        "Track inventory per product slot and prevent selling an out-of-stock item.",
                        "Return change or reject the transaction if payment is insufficient.",
                        "Dispense the selected product and update inventory atomically.",
                        "Support adding a new product slot or payment method later with minimal changes.",
                        "Handle an out-of-service/maintenance mode safely."
                ),
                List.of(
                        "What state is the machine in at each step (idle, has money, dispensing, out of stock)? Should that be explicit?",
                        "How do you keep payment-method details out of the core selection/dispensing logic?",
                        "What happens if dispensing physically fails after payment was already accepted?"
                ),
                List.of(
                        "A Payment interface with accept()/refund() keeps coins vs. card out of the main flow.",
                        "A small explicit state machine (IDLE, HAS_MONEY, DISPENSING, SOLD_OUT, MAINTENANCE) avoids a tangle of booleans.",
                        "A Slot holding both a Product and its remaining quantity keeps inventory logic in one place."
                ),
                Rubric.standard(),
                List.of(
                        new Concept("a product abstraction", List.of("Product", "Item"), true),
                        new Concept("an inventory/slot abstraction", List.of("Inventory", "Slot", "Stock"), true),
                        new Concept("a pluggable payment strategy", List.of("Payment", "PaymentMethod", "PaymentStrategy", "CoinAcceptor", "CardReader"), true),
                        new Concept("an explicit machine state/state machine", List.of("VendingMachineState", "State", "MachineState"), false),
                        new Concept("the vending machine container", List.of("VendingMachine"), true),
                        new Concept("a dispensing responsibility", List.of("Dispenser", "dispense"), false)
                )
        );
    }
}

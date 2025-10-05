import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println("=== Route Planner Application Start ===");
        // Set file paths for data sources
        String roadsCsvPath = "/resources/roads.csv";
        String attractionsCsvPath = "/resources/attractions.csv";

        System.out.println("\nLoading data...");
        Graph roadNetwork = new Graph();
        AttractionMapper attractionMapper = new AttractionMapper();

        try {
            // Load road network and attractions data from CSV files
            roadNetwork.loadRoads(roadsCsvPath);
            attractionMapper.loadAttractions(attractionsCsvPath);
        } catch (Exception e) {
            System.err.println("Error during data loading: " + e.getMessage());
            e.printStackTrace();
            System.out.println("Exiting due to data loading failure.");
            return;
        }

        // Check if data loaded successfully
        if (roadNetwork.getCities() == null || roadNetwork.getCities().isEmpty() || attractionMapper.getAttractionCount() == 0) {
            System.err.println("Error: Data not loaded correctly (Graph might be empty or AttractionMapper failed). Exiting.");
            return;
        }
        System.out.println("Data loading complete. " + roadNetwork.getCities().size() + " cities found.");

        // Create planner for route comparison
        RoutePlannerComparison planner = new RoutePlannerComparison(roadNetwork, attractionMapper);

        System.out.println("\nRunning specified test cases from Task B (Comparing Optimal vs. Heuristic)...");

        // Run several test cases with different start/end/attractions
        runTestCase(planner, "Houston TX", "Philadelphia PA", Collections.emptyList());
        runTestCase(planner, "Philadelphia PA", "San Antonio TX", Arrays.asList("Hollywood Sign"));
        runTestCase(planner, "San Jose CA", "Phoenix AZ", Arrays.asList("Liberty Bell", "Millennium Park"));
        runTestCase(planner, "New York NY", "San Diego CA", Arrays.asList("Millennium Park", "NASA Space Center", "The Alamo"));

        System.out.println("\n=== Route Planner Application End ===");
    }

    // Run a single test case and compare both algorithms
    private static void runTestCase(RoutePlannerComparison planner, String start, String end, List<String> attractions) {
        System.out.println("\n==================================================");
        System.out.println("Test Case:");
        System.out.println("  From: " + start);
        System.out.println("  To:   " + end);
        System.out.println("  Via:  " + (attractions.isEmpty() ? "None" : attractions));
        System.out.println("--------------------------------------------------");

        // Run optimal algorithm (Dijkstra + permutations)
        System.out.println("--> Running Optimal Algorithm (Dijkstra + Permutations)...");
        List<String> optimalPath = null;
        double optimalDistance = -1.0;
        long startTimeOptimal = System.nanoTime();
        try {
            optimalPath = planner.route(start, end, attractions);
            optimalDistance = planner.getLastCalculatedOptimalDistance();
        } catch (Exception e) {
            System.err.println("  Optimal Algorithm Error: " + e.getMessage());
            e.printStackTrace();
        }
        long endTimeOptimal = System.nanoTime();
        double durationOptimalMs = (endTimeOptimal - startTimeOptimal) / 1_000_000.0;

        // Run heuristic algorithm (Nearest Neighbor)
        System.out.println("\n--> Running Heuristic Algorithm (Nearest Neighbor)...");
        List<String> heuristicPath = null;
        double heuristicDistance = -1.0;
        long startTimeHeuristic = System.nanoTime();
        try {
            heuristicPath = planner.routeHeuristic(start, end, attractions);
            heuristicDistance = planner.getLastCalculatedHeuristicDistance();
        } catch (Exception e) {
            System.err.println("  Heuristic Algorithm Error: " + e.getMessage());
            e.printStackTrace();
        }
        long endTimeHeuristic = System.nanoTime();
        double durationHeuristicMs = (endTimeHeuristic - startTimeHeuristic) / 1_000_000.0;

        // Print results for both algorithms
        System.out.println("\n--- Comparison Results ---");

        System.out.println("\nOptimal (Dijkstra + Permutations):");
        if (optimalPath != null && optimalDistance >= 0) {
            System.out.println("  Route: " + optimalPath);
            System.out.printf("  Distance: %.1f miles%n", optimalDistance);
        } else {
            System.out.println("  Route: Not found or path is impossible.");
            System.out.println("  Distance: N/A");
        }
        System.out.printf("  (Optional) Execution Time: %.3f ms%n", durationOptimalMs);

        System.out.println("\nHeuristic (Nearest Neighbor):");
        if (heuristicPath != null && heuristicDistance >= 0) {
            System.out.println("  Route: " + heuristicPath);
            System.out.printf("  Distance: %.1f miles%n", heuristicDistance);
        } else {
            System.out.println("  Route: Not found or path is impossible.");
            System.out.println("  Distance: N/A");
        }
        System.out.printf("  (Optional) Execution Time: %.3f ms%n", durationHeuristicMs);

        System.out.println("==================================================");
    }
}

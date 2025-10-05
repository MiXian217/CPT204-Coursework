import java.util.*;

// NodeEntry is used for the priority queue in Dijkstra's algorithm
record NodeEntry(String city, double distance) implements Comparable<NodeEntry> {
    @Override
    public int compareTo(NodeEntry other) {
        return Double.compare(this.distance, other.distance);
    }
}

// Stores the result of Dijkstra's algorithm
record DijkstraResult(Map<String, Double> distances, Map<String, String> predecessors) {}

public class RoutePlanner {

    private final Graph roadNetwork;
    private final AttractionMapper attractionMapper;
    private double lastCalculatedOptimalDistance = -1.0;

    public RoutePlanner(Graph roadNetwork, AttractionMapper attractionMapper) {
        if (roadNetwork == null || attractionMapper == null) {
            throw new IllegalArgumentException("Graph and AttractionMapper cannot be null.");
        }
        this.roadNetwork = roadNetwork;
        this.attractionMapper = attractionMapper;
    }

    public double getLastCalculatedOptimalDistance() {
        return lastCalculatedOptimalDistance;
    }

    // Main method to find the optimal route visiting all attractions
    public List<String> route(String startingCity, String endingCity, List<String> attractionNames) {
        System.out.println("\n--- Route Planning (Optimal: Dijkstra + Permutations) ---");
        System.out.println("Start: " + startingCity);
        System.out.println("Destination: " + endingCity);
        System.out.println("Attractions: " + (attractionNames == null || attractionNames.isEmpty() ? "None" : attractionNames));
        this.lastCalculatedOptimalDistance = -1.0;

        // Check if start and end cities exist in the graph
        if (!roadNetwork.getCities().contains(startingCity)) {
            System.err.println("Error: Start city '" + startingCity + "' not found in the road network.");
            return null;
        }
        if (!roadNetwork.getCities().contains(endingCity)) {
            System.err.println("Error: Destination city '" + endingCity + "' not found in the road network.");
            return null;
        }

        // Map attractions to their cities, avoid duplicates and start/end
        List<String> intermediateCities = new ArrayList<>();
        Set<String> uniqueIntermediateCities = new HashSet<>();
        if (attractionNames != null) {
            for (String attractionName : attractionNames) {
                String city = attractionMapper.getCityForAttraction(attractionName);
                if (city == null) {
                    System.err.println("Warning: Attraction '" + attractionName + "' not found. Skipping.");
                } else if (!city.equals(startingCity) && !city.equals(endingCity) && uniqueIntermediateCities.add(city)) {
                    intermediateCities.add(city);
                } else {
                    // System.out.println("Info: Attraction '" + attractionName + "' is at start/end city or already listed. Implicitly visited.");
                }
            }
        }
        System.out.println("Intermediate cities to visit: " + (intermediateCities.isEmpty() ? "None" : intermediateCities));
        int k = intermediateCities.size();

        // If no intermediate cities, just run Dijkstra from start to end
        if (k == 0) {
            System.out.println("No intermediate attractions. Calculating direct shortest path...");
            DijkstraResultComparison result = runDijkstra(startingCity, roadNetwork);
            List<String> path = reconstructPath(startingCity, endingCity, result.predecessors());

            if (path != null) {
                this.lastCalculatedOptimalDistance = result.distances().getOrDefault(endingCity, Double.POSITIVE_INFINITY);
                if (this.lastCalculatedOptimalDistance == Double.POSITIVE_INFINITY) {
                    System.err.println("Error: Destination city '" + endingCity + "' is unreachable from start city '" + startingCity + "'.");
                    this.lastCalculatedOptimalDistance = -1.0; // Reset distance
                    return null;
                }
                System.out.printf("Direct Path Found. Distance: %.1f miles%n", this.lastCalculatedOptimalDistance);
                System.out.println("----------------------");
                return path;
            } else {
                System.err.println("Error: Cannot reconstruct path from '" + startingCity + "' to '" + endingCity + "'.");
                System.out.println("----------------------");
                return null;
            }
        }

        System.out.println("Calculating shortest path visiting " + k + " intermediate cities...");

        // Pre-calculate shortest paths between all key points (start, end, and intermediate cities)
        Set<String> keyPoints = new HashSet<>(intermediateCities);
        keyPoints.add(startingCity);
        keyPoints.add(endingCity);

        System.out.println("Pre-calculating shortest paths between " + keyPoints.size() + " key points...");
        Map<String, DijkstraResultComparison> allDijkstraResults = new HashMap<>();
        for (String point : keyPoints) {
            if (roadNetwork.getCities().contains(point)) {
                allDijkstraResults.put(point, runDijkstra(point, roadNetwork));
            } else {
                System.err.println("Error: Key point '" + point + "' not found in graph during pre-calculation.");
                System.out.println("----------------------");
                return null;
            }
        }
        System.out.println("Finished pre-calculation.");

        // Generate all permutations of the intermediate cities
        List<List<String>> permutations = generatePermutations(intermediateCities);
        // System.out.println("Generated " + permutations.size() + " permutations of intermediate cities."); // Comments can be removed for debugging

        double minTotalDistance = Double.POSITIVE_INFINITY;
        List<String> bestPermutation = null;
        List<List<String>> bestPathSegments = null; // Store each section of the best path

        int evaluatedCount = 0;
        for (List<String> currentPermutation : permutations) {
            double currentTotalDistance = 0.0;
            boolean possible = true;
            List<List<String>> currentPathSegments = new ArrayList<>();
            String previousPoint = startingCity;

            List<String> pointsInOrder = new ArrayList<>();
            pointsInOrder.add(startingCity);
            pointsInOrder.addAll(currentPermutation);
            pointsInOrder.add(endingCity);

            // Calculate the total distance for this permutation
            for (int i = 0; i < pointsInOrder.size() - 1; i++) {
                String from = pointsInOrder.get(i);
                String to = pointsInOrder.get(i + 1);

                double segmentDist = getDistance(from, to, allDijkstraResults);
                List<String> segmentPath = getPath(from, to, allDijkstraResults);

                if (segmentDist == Double.POSITIVE_INFINITY || segmentPath == null) {
                    possible = false;
                    break;
                }
                currentTotalDistance += segmentDist;
                currentPathSegments.add(segmentPath);
            }

            // Update the best permutation if this one is better
            if (possible && currentTotalDistance < minTotalDistance) {
                minTotalDistance = currentTotalDistance;
                bestPermutation = currentPermutation;
                bestPathSegments = currentPathSegments;
            }
            evaluatedCount++;
            if (evaluatedCount % 1000 == 0 && permutations.size() > 1000) {
                System.out.println("Evaluated " + evaluatedCount + "/" + permutations.size() + " permutations...");
            }
        }

        if (bestPermutation == null) {
            System.err.println("Error: Could not find a valid route visiting all specified attractions.");
            System.out.println("----------------------");
            return null;
        }

        // Concatenate all path segments into the final route
        List<String> finalOptimalPath = concatenatePaths(bestPathSegments);

        this.lastCalculatedOptimalDistance = minTotalDistance;
        System.out.println("Optimal Permutation Found: " + bestPermutation);
        System.out.printf("Minimum Total Distance: %.1f miles%n", minTotalDistance);
        // System.out.println("Optimal Route: " + finalOptimalPath);

        System.out.println("----------------------");
        return finalOptimalPath;
    }

    // Dijkstra's algorithm implementation
    private DijkstraResultComparison runDijkstra(String startCity, Graph graph) {
        Set<String> cities = graph.getCities();
        if (!cities.contains(startCity)) {
            return new DijkstraResultComparison(new HashMap<>(), new HashMap<>());
        }

        Map<String, Double> distances = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();
        PriorityQueue<NodeEntry> priorityQueue = new PriorityQueue<>();
        Set<String> visited = new HashSet<>();

        for (String city : cities) {
            distances.put(city, Double.POSITIVE_INFINITY);
        }
        distances.put(startCity, 0.0);
        priorityQueue.add(new NodeEntry(startCity, 0.0));

        while (!priorityQueue.isEmpty()) {
            NodeEntry currentEntry = priorityQueue.poll();
            String currentCity = currentEntry.city();
            double currentDistance = currentEntry.distance();

            if (!visited.add(currentCity)) {
                continue;
            }

            List<Graph.Edge> neighbors = graph.getNeighbors(currentCity);
            if (neighbors == null) continue;

            for (Graph.Edge edge : neighbors) {
                String neighborCity = edge.targetCity();
                if (!distances.containsKey(neighborCity) || visited.contains(neighborCity)) {
                    continue;
                }

                double edgeWeight = edge.distance();
                double newDist = currentDistance + edgeWeight;

                if (newDist < distances.get(neighborCity)) {
                    distances.put(neighborCity, newDist);
                    predecessors.put(neighborCity, currentCity);
                    priorityQueue.add(new NodeEntry(neighborCity, newDist));
                }
            }
        }
        return new DijkstraResultComparison(distances, predecessors);
    }

    // Reconstructs the path from start to end using the predecessor map
    private List<String> reconstructPath(String start, String end, Map<String, String> predecessors) {
        LinkedList<String> path = new LinkedList<>();
        String current = end;

        if (start.equals(end)) {
            if (roadNetwork.getCities().contains(start)) {
                path.add(start);
                return path;
            } else {
                return null;
            }
        }


        if (!predecessors.containsKey(end)) {
            return null;
        }


        while (current != null) {
            path.addFirst(current);
            if (current.equals(start)) {
                break;
            }
            current = predecessors.get(current);
            if (current == null && !path.getFirst().equals(start)) {
                System.err.println("Error: Path reconstruction failed - predecessor map incomplete for path to " + start);
                return null;
            }
        }

        if (path.isEmpty() || !path.getFirst().equals(start)) {
            System.err.println("Error: Path reconstruction resulted in an invalid path for " + start + " -> " + end);
            return null;
        }

        return path;
    }

    // Generate all permutations of a list (used for visiting all attractions)
    private List<List<String>> generatePermutations(List<String> items) {
        List<List<String>> allPermutations = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            allPermutations.add(new ArrayList<>());
            return allPermutations;
        }
        generatePermutationsRecursive(new ArrayList<>(items), 0, allPermutations); // Use helper for efficiency
        return allPermutations;
    }
    private void generatePermutationsRecursive(List<String> currentList, int startIndex, List<List<String>> allPermutations) {
        if (startIndex == currentList.size() - 1) {
            allPermutations.add(new ArrayList<>(currentList));
        } else {
            for (int i = startIndex; i < currentList.size(); i++) {
                Collections.swap(currentList, startIndex, i);
                generatePermutationsRecursive(currentList, startIndex + 1, allPermutations);
                Collections.swap(currentList, startIndex, i);
            }
        }
    }

    // Helper to get distance between two cities from precomputed Dijkstra results
    private double getDistance(String fromCity, String toCity, Map<String, DijkstraResultComparison> allResults) {
        if (allResults.containsKey(fromCity)) {
            return allResults.get(fromCity).distances().getOrDefault(toCity, Double.POSITIVE_INFINITY);
        }
        return Double.POSITIVE_INFINITY;
    }

    // Helper to get the path between two cities from precomputed Dijkstra results
    private List<String> getPath(String fromCity, String toCity, Map<String, DijkstraResultComparison> allResults) {
        if (fromCity.equals(toCity)) {
            // Make sure the city exists before returning a path to itself
            if (roadNetwork.getCities().contains(fromCity)) {
                return Collections.singletonList(fromCity);
            } else {
                return null;
            }
        }
        if (allResults.containsKey(fromCity)) {
            DijkstraResultComparison result = allResults.get(fromCity);
            if (result.distances().getOrDefault(toCity, Double.POSITIVE_INFINITY) == Double.POSITIVE_INFINITY){
                return null;
            }
            return reconstructPath(fromCity, toCity, result.predecessors());
        }
        return null;
    }

    // Concatenate all path segments into a single path
    private List<String> concatenatePaths(List<List<String>> pathSegments) {
        if (pathSegments == null || pathSegments.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> finalPath = new ArrayList<>();
        boolean firstSegment = true;

        for (List<String> segment : pathSegments) {
            if (segment == null || segment.isEmpty()) {
                System.err.println("Error: Found null or empty segment during path concatenation.");
                return null; // Indicate failure
            }

            if (firstSegment) {
                finalPath.addAll(segment);
                firstSegment = false;
            } else {
                if (finalPath.isEmpty() || !finalPath.get(finalPath.size() - 1).equals(segment.get(0))) {
                    System.err.println("Error: Path segments do not connect properly. Last element was " +
                            (finalPath.isEmpty() ? "null" : finalPath.get(finalPath.size()-1)) +
                            ", next segment starts with " + segment.get(0));
                    return null;
                }
                finalPath.addAll(segment.subList(1, segment.size()));
            }
        }
        return finalPath;
    }

}

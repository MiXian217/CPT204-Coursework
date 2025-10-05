import java.util.*;

public class RoutePlannerComparison {

    private final Graph roadNetwork;
    private final AttractionMapper attractionMapper;
    private double lastCalculatedOptimalDistance = -1.0;
    private double lastCalculatedHeuristicDistance = -1.0; // New variable

    public RoutePlannerComparison(Graph roadNetwork, AttractionMapper attractionMapper) {
        if (roadNetwork == null || attractionMapper == null) {
            throw new IllegalArgumentException("Graph and AttractionMapper cannot be null.");
        }
        this.roadNetwork = roadNetwork;
        this.attractionMapper = attractionMapper;
    }

    public double getLastCalculatedOptimalDistance() {
        return lastCalculatedOptimalDistance;
    }

    public double getLastCalculatedHeuristicDistance() {
        return lastCalculatedHeuristicDistance;
    }

    // Find the optimal route (Dijkstra + permutations)
    public List<String> route(String startingCity, String endingCity, List<String> attractionNames) {
        System.out.println("\n--- Route Planning (Optimal: Dijkstra + Permutations) ---");
        Map<String, DijkstraResultComparison> allDijkstraResults = precomputeDijkstraForAllKeyPoints(startingCity, endingCity, attractionNames);
        if (allDijkstraResults == null) {
            // Handle error during precomputation (e.g., start/end city not found)
            System.err.println("Error during pre-computation for optimal route.");
            return null;
        }


        List<String> intermediateCities = getIntermediateCities(startingCity, endingCity, attractionNames);
        if (intermediateCities.isEmpty()) {
            // No attractions, just shortest path
            DijkstraResultComparison directResult = allDijkstraResults.get(startingCity); // Assuming precompute included start
            List<String> path = reconstructPath(startingCity, endingCity, directResult.predecessors());
            if (path != null) {
                this.lastCalculatedOptimalDistance = directResult.distances().getOrDefault(endingCity, Double.POSITIVE_INFINITY);
                if (this.lastCalculatedOptimalDistance == Double.POSITIVE_INFINITY) { /* handle unreachable */ return null; }
                return path;
            } else { /* handle error */ return null; }
        }


        System.out.println("Calculating shortest path visiting " + intermediateCities.size() + " intermediate cities...");
        List<List<String>> permutations = generatePermutations(intermediateCities);
        double minTotalDistance = Double.POSITIVE_INFINITY;
        List<String> bestPermutation = null;
        List<List<String>> bestPathSegments = null;

        // Try all permutations to find the shortest route
        for (List<String> currentPermutation : permutations) {

            double currentTotalDistance = 0.0;
            boolean possible = true;
            List<List<String>> currentPathSegments = new ArrayList<>();
            String previousPoint = startingCity;

            List<String> pointsInOrder = new ArrayList<>();
            pointsInOrder.add(startingCity);
            pointsInOrder.addAll(currentPermutation);
            pointsInOrder.add(endingCity);

            for (int i = 0; i < pointsInOrder.size() - 1; i++) {
                String from = pointsInOrder.get(i);
                String to = pointsInOrder.get(i + 1);
                double segmentDist = getDistance(from, to, allDijkstraResults);
                List<String> segmentPath = getPath(from, to, allDijkstraResults);
                if (segmentDist == Double.POSITIVE_INFINITY || segmentPath == null) {
                    possible = false; break;
                }
                currentTotalDistance += segmentDist;
                currentPathSegments.add(segmentPath); // Assuming we need segments
            }
            if (possible && currentTotalDistance < minTotalDistance) {
                minTotalDistance = currentTotalDistance;
                bestPermutation = currentPermutation;
                bestPathSegments = currentPathSegments; // Store segments if needed
            }
        }


        if (bestPermutation == null) {
            System.err.println("Error: Could not find a valid route visiting all specified attractions (Optimal).");
            return null;
        }

        List<String> finalOptimalPath = concatenatePaths(bestPathSegments); // Reconstruct the path
        this.lastCalculatedOptimalDistance = minTotalDistance;
        System.out.println("Optimal Permutation Found: " + bestPermutation);
        System.out.printf("Minimum Total Distance (Optimal): %.1f miles%n", minTotalDistance);
        System.out.println("----------------------");
        return finalOptimalPath; // Make sure path reconstruction is correct

    }

    // Heuristic route using Nearest Neighbor
    public List<String> routeHeuristic(String startingCity, String endingCity, List<String> attractionNames) {
        System.out.println("\n--- Route Planning (Heuristic: Nearest Neighbor) ---");
        this.lastCalculatedHeuristicDistance = -1.0; // Reset distance

        Map<String, DijkstraResultComparison> allDijkstraResults = precomputeDijkstraForAllKeyPoints(startingCity, endingCity, attractionNames);
        if (allDijkstraResults == null) {
            System.err.println("Error during pre-computation for heuristic route.");
            return null;
        }

        List<String> intermediateCities = getIntermediateCities(startingCity, endingCity, attractionNames);
        System.out.println("Intermediate cities to visit: " + (intermediateCities.isEmpty() ? "None" : intermediateCities));

        if (intermediateCities.isEmpty()) {
            // No attractions, just shortest path
            System.out.println("No intermediate attractions. Calculating direct shortest path...");
            DijkstraResultComparison directResult = allDijkstraResults.get(startingCity);
            if (directResult == null) return null; // Should not happen if precompute worked
            List<String> path = reconstructPath(startingCity, endingCity, directResult.predecessors());
            if (path != null) {
                this.lastCalculatedHeuristicDistance = directResult.distances().getOrDefault(endingCity, Double.POSITIVE_INFINITY);
                if (this.lastCalculatedHeuristicDistance == Double.POSITIVE_INFINITY) {
                    System.err.println("Error: Destination city '" + endingCity + "' is unreachable from start city '" + startingCity + "'.");
                    this.lastCalculatedHeuristicDistance = -1.0;
                    return null;
                }
                System.out.printf("Direct Path Found (Heuristic). Distance: %.1f miles%n", this.lastCalculatedHeuristicDistance);
                System.out.println("----------------------");
                return path;
            } else {
                System.err.println("Error: Cannot reconstruct path from '" + startingCity + "' to '" + endingCity + "'.");
                System.out.println("----------------------");
                return null;
            }
        }

        System.out.println("Calculating heuristic path visiting " + intermediateCities.size() + " intermediate cities...");
        Set<String> unvisitedAttractionCities = new HashSet<>(intermediateCities);
        List<List<String>> heuristicPathSegments = new ArrayList<>();
        double currentTotalDistance = 0.0;
        String currentCity = startingCity;
        List<String> heuristicVisitOrder = new ArrayList<>(); // Record the order visited

        // Greedily visit the nearest unvisited attraction city
        while (!unvisitedAttractionCities.isEmpty()) {
            String nearestCity = null;
            double minDistance = Double.POSITIVE_INFINITY;


            for (String potentialNext : unvisitedAttractionCities) {
                double dist = getDistance(currentCity, potentialNext, allDijkstraResults);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestCity = potentialNext;
                }
            }

            if (nearestCity == null || minDistance == Double.POSITIVE_INFINITY) {
                System.err.println("Error: Cannot find reachable unvisited attraction from " + currentCity);
                return null;
            }

            List<String> segmentPath = getPath(currentCity, nearestCity, allDijkstraResults);
            if (segmentPath == null) {
                System.err.println("Error: Cannot reconstruct path segment from " + currentCity + " to " + nearestCity);
                return null;
            }

            currentTotalDistance += minDistance;
            heuristicPathSegments.add(segmentPath);
            heuristicVisitOrder.add(nearestCity);


            currentCity = nearestCity;
            unvisitedAttractionCities.remove(nearestCity);
        }

        // Finally, go from last attraction to ending city
        double finalSegmentDist = getDistance(currentCity, endingCity, allDijkstraResults);
        List<String> finalSegmentPath = getPath(currentCity, endingCity, allDijkstraResults);

        if (finalSegmentPath == null || finalSegmentDist == Double.POSITIVE_INFINITY) {
            System.err.println("Error: Cannot reach final destination " + endingCity + " from " + currentCity);
            return null;
        }

        currentTotalDistance += finalSegmentDist;
        heuristicPathSegments.add(finalSegmentPath);

        // Combine all segments into the final path
        List<String> finalHeuristicPath = concatenatePaths(heuristicPathSegments);
        this.lastCalculatedHeuristicDistance = currentTotalDistance;

        System.out.println("Heuristic Visit Order: " + heuristicVisitOrder);
        System.out.printf("Total Distance (Heuristic): %.1f miles%n", currentTotalDistance);
        // System.out.println("Heuristic Route: " + finalHeuristicPath); // Optional: print full path
        System.out.println("----------------------");

        return finalHeuristicPath;
    }

    // Get all intermediate cities (attractions) to visit, excluding start/end
    private List<String> getIntermediateCities(String startingCity, String endingCity, List<String> attractionNames) {
        Set<String> uniqueIntermediateCities = new HashSet<>();
        List<String> intermediateCities = new ArrayList<>();
        if (attractionNames != null) {
            for (String attractionName : attractionNames) {
                String city = attractionMapper.getCityForAttraction(attractionName);
                if (city != null && !city.equals(startingCity) && !city.equals(endingCity) && uniqueIntermediateCities.add(city)) {
                    intermediateCities.add(city);
                } else if (city == null){
                    System.err.println("Warning: Attraction '" + attractionName + "' not found during intermediate city processing. Skipping.");
                }
            }
        }
        return intermediateCities;
    }

    // Precompute Dijkstra results for all key points (start, end, and attractions)
    private Map<String, DijkstraResultComparison> precomputeDijkstraForAllKeyPoints(String startingCity, String endingCity, List<String> attractionNames) {
        System.out.println("Pre-calculating shortest paths between key points...");
        Map<String, DijkstraResultComparison> allResults = new HashMap<>();

        Set<String> keyPoints = new HashSet<>(getIntermediateCities(startingCity, endingCity, attractionNames));
        keyPoints.add(startingCity);
        keyPoints.add(endingCity);

        // Check if start/end exist
        if (!roadNetwork.getCities().contains(startingCity)) {
            System.err.println("Error: Start city '" + startingCity + "' not found in the road network.");
            return null;
        }
        if (!roadNetwork.getCities().contains(endingCity)) {
            System.err.println("Error: Destination city '" + endingCity + "' not found in the road network.");
            return null;
        }

        // Run Dijkstra for each key point
        for (String point : keyPoints) {
            if (roadNetwork.getCities().contains(point)) {
                allResults.put(point, runDijkstra(point, roadNetwork));
            } else {

                System.err.println("Error: Key point '" + point + "' (likely from an attraction) not found in graph during pre-calculation.");
            }
        }
        System.out.println("Finished pre-calculation for " + keyPoints.size() + " key points.");
        return allResults;
    }

    // Get distance between two cities from precomputed Dijkstra results
    private double getDistance(String fromCity, String toCity, Map<String, DijkstraResultComparison> allResults) {
        if (allResults.containsKey(fromCity)) {
            return allResults.get(fromCity).distances().getOrDefault(toCity, Double.POSITIVE_INFINITY);
        }
        return Double.POSITIVE_INFINITY;
    }

    // Get path between two cities from precomputed Dijkstra results
    private List<String> getPath(String fromCity, String toCity, Map<String, DijkstraResultComparison> allResults) {
        if (fromCity.equals(toCity)) {
            return roadNetwork.getCities().contains(fromCity) ? Collections.singletonList(fromCity) : null;
        }
        if (allResults.containsKey(fromCity)) {
            DijkstraResultComparison result = allResults.get(fromCity);
            if (result.distances().getOrDefault(toCity, Double.POSITIVE_INFINITY) == Double.POSITIVE_INFINITY) {
                return null; // Unreachable
            }
            return reconstructPath(fromCity, toCity, result.predecessors());
        }
        return null;
    }

    // Dijkstra's algorithm for shortest paths
    private DijkstraResultComparison runDijkstra(String startCity, Graph graph) {
        // The previous Dijkstra implementation
        Set<String> cities = graph.getCities();
        if (!cities.contains(startCity)) { return new DijkstraResultComparison(new HashMap<>(), new HashMap<>()); }
        Map<String, Double> distances = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();
        PriorityQueue<NodeEntryComparison> priorityQueue = new PriorityQueue<>();
        Set<String> visited = new HashSet<>();
        for (String city : cities) { distances.put(city, Double.POSITIVE_INFINITY); }
        distances.put(startCity, 0.0);
        priorityQueue.add(new NodeEntryComparison(startCity, 0.0));
        while (!priorityQueue.isEmpty()) {
            NodeEntryComparison currentEntry = priorityQueue.poll(); String currentCity = currentEntry.city(); double currentDistance = currentEntry.distance();
            if (!visited.add(currentCity)) continue;
            List<Graph.Edge> neighbors = graph.getNeighbors(currentCity);
            if (neighbors == null) continue;
            for (Graph.Edge edge : neighbors) {
                String neighborCity = edge.targetCity();
                if (!distances.containsKey(neighborCity) || visited.contains(neighborCity)) continue;
                double edgeWeight = edge.distance(); double newDist = currentDistance + edgeWeight;
                if (newDist < distances.get(neighborCity)) {
                    distances.put(neighborCity, newDist); predecessors.put(neighborCity, currentCity);
                    priorityQueue.add(new NodeEntryComparison(neighborCity, newDist));
                }
            }
        } return new DijkstraResultComparison(distances, predecessors);
    }
    
    // Reconstruct path from predecessor map
    private List<String> reconstructPath(String start, String end, Map<String, String> predecessors) {
        LinkedList<String> path = new LinkedList<>(); String current = end;
        if (start.equals(end)) { return roadNetwork.getCities().contains(start) ? Collections.singletonList(start) : null; }
        if (!predecessors.containsKey(end)) { return null; } // Unreachable
        while (current != null) {
            path.addFirst(current);
            if (current.equals(start)) break;
            current = predecessors.get(current);
            if (current == null && !path.getFirst().equals(start)) return null; // Path broken
        }
        if (path.isEmpty() || !path.getFirst().equals(start)) return null; // Invalid path
        return path;
    }
    
    // Generate all permutations of a list (for optimal route)
    private List<List<String>> generatePermutations(List<String> items) {
        List<List<String>> allPermutations = new ArrayList<>();
        if (items == null || items.isEmpty()) { allPermutations.add(new ArrayList<>()); return allPermutations; }
        generatePermutationsRecursive(new ArrayList<>(items), 0, allPermutations);
        return allPermutations;
    }
    private void generatePermutationsRecursive(List<String> currentList, int startIndex, List<List<String>> allPermutations) {
        if (startIndex == currentList.size() - 1) { allPermutations.add(new ArrayList<>(currentList)); }
        else {
            for (int i = startIndex; i < currentList.size(); i++) {
                Collections.swap(currentList, startIndex, i);
                generatePermutationsRecursive(currentList, startIndex + 1, allPermutations);
                Collections.swap(currentList, startIndex, i); // Backtrack
            }
        }
    }
    
    // Concatenate all path segments into a single path
    private List<String> concatenatePaths(List<List<String>> pathSegments) {
        if (pathSegments == null || pathSegments.isEmpty()) { return new ArrayList<>(); }
        List<String> finalPath = new ArrayList<>(); boolean firstSegment = true;
        for (List<String> segment : pathSegments) {
            if (segment == null || segment.isEmpty()) { System.err.println("Error: Found null/empty segment."); return null; }
            if (firstSegment) { finalPath.addAll(segment); firstSegment = false; }
            else {
                if (finalPath.isEmpty() || !finalPath.get(finalPath.size() - 1).equals(segment.get(0))) { System.err.println("Error: Path segments do not connect."); return null; }
                finalPath.addAll(segment.subList(1, segment.size()));
            }
        } return finalPath;
    }

}

// NodeEntry for Dijkstra's priority queue
record NodeEntryComparison(String city, double distance) implements Comparable<NodeEntryComparison> {
    @Override public int compareTo(NodeEntryComparison other) { return Double.compare(this.distance, other.distance); }
}

// Dijkstra result record
record DijkstraResultComparison(Map<String, Double> distances, Map<String, String> predecessors) {}
// ---------------------------------------------------------------------------

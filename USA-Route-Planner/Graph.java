import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Graph {
    // Edge record to represent a connection to a target city and its distance
    public record Edge(String targetCity, double distance) {}

    // Adjacency list to store the graph structure
    private final Map<String, List<Edge>> adjacencyList;

    public Graph() {
        this.adjacencyList = new HashMap<>();
    }

    // Add a bidirectional road between two cities
    public void addRoad(String cityA, String cityB, double distance) {
        this.adjacencyList.computeIfAbsent(cityA, k -> new ArrayList<>()).add(new Edge(cityB, distance));
        this.adjacencyList.computeIfAbsent(cityB, k -> new ArrayList<>()).add(new Edge(cityA, distance));
    }

    // Get all neighboring edges for a given city
    public List<Edge> getNeighbors(String city) {
        return this.adjacencyList.getOrDefault(city, Collections.emptyList());
    }

    // Get all cities in the graph
    public Set<String> getCities() {
        return this.adjacencyList.keySet();
    }

    // Load road data from a CSV resource file
    public void loadRoads(String resourcePath) {
        System.out.println("Loading roads from: " + resourcePath);
        try (BufferedReader reader = new BufferedReader(
            new java.io.InputStreamReader(
                getClass().getResourceAsStream(resourcePath), "UTF-8"))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()  ) {
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length == 3) {
                    try {
                        String cityA = parts[0].trim();
                        String cityB = parts[1].trim();
                        double distance = Double.parseDouble(parts[2].trim());
                        addRoad(cityA, cityB, distance);
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Skipping line " + lineNum + " due to invalid distance format: " + line);
                    } catch (Exception e) {
                        System.err.println("Warning: Skipping line " + lineNum + " due to unexpected error during parsing: " + line + " - " + e.getMessage());
                    }
                } else {
                    System.err.println("Warning: Skipping line " + lineNum + " due to incorrect number of columns: " + line);
                }
            }
            System.out.println("Finished loading roads. Found " + getCities().size() + " cities.");
        } catch (IOException e) {
            System.err.println("Error reading roads file: " + resourcePath);
            e.printStackTrace();
        }
    }
}

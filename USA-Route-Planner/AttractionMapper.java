import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AttractionMapper {

    // Map to store attraction name to city mapping
    private final Map<String, String> attractionToCityMap;

    public AttractionMapper() {
        this.attractionToCityMap = new HashMap<>();
    }

    // Get the city for a given attraction
    public String getCityForAttraction(String attractionName) {
        return this.attractionToCityMap.get(attractionName);
    }

    // Get the total number of attractions loaded
    public int getAttractionCount() {
        return this.attractionToCityMap.size();
    }

    // Load attractions data from a CSV resource file
    public void loadAttractions(String resourcePath) {
    System.out.println("Loading attractions from: " + resourcePath);
    try (BufferedReader reader = new BufferedReader(
            new java.io.InputStreamReader(
                getClass().getResourceAsStream(resourcePath), "UTF-8"))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty() ) {
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length == 2) {
                    String attractionName = parts[0].trim();
                    String locationCity = parts[1].trim();
                    this.attractionToCityMap.put(attractionName, locationCity);
                } else {
                    System.err.println("Warning: Skipping line " + lineNum + " in attractions file due to incorrect format: " + line);
                }
            }
            System.out.println("Finished loading attractions. Found " + getAttractionCount() + " attractions.");
        } catch (IOException e) {
            System.err.println("Error reading attractions file: " + resourcePath);
            e.printStackTrace();
        }
    }
}


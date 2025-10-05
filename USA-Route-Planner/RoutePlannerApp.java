import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

public class RoutePlannerApp extends Application {
    private Graph roadNetwork;
    private AttractionMapper attractionMapper;
    private RoutePlannerComparison planner;
    private Pane graphPane;
    private Map<String, Point2D> cityPixelCoordinates;
    private Map<String, Node> cityNodes;
    private TextField startCityField;
    private TextField endCityField;
    private TextField attractionsField;
    private TextArea resultArea;
    private Button planButton;
    private Button resetButton;

    private Image usMapImage;
    private String mapImagePath = "/resources/map.gif"; // path of the usa map
    private double mapMinLatitude = 21.4;   // : south
    private double mapMaxLatitude = 50.0;   // : north
    private double mapMinLongitude = -125.0; // : west
    private double mapMaxLongitude = -68.0;  // : east

    private Map<String, Point2DGeographic> cityGeographicCoordinates;

    // Helper class for storing geographic coordinates
    private static class Point2DGeographic {
        double latitude;
        double longitude;
        public Point2DGeographic(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    // Helper class for state label data
    private static class StateLabelData {
        String abbr; double x; double y;
        StateLabelData(String abbr, double x, double y) { this.abbr = abbr; this.x = x; this.y = y; }
    }
    private List<StateLabelData> stateLabelDataList = Arrays.asList(

    );

    // Helper class for city name resolution results
    private static class CityResolutionResult {
        final String resolvedName;
        final String errorMessage;
        CityResolutionResult(String name, String error) { this.resolvedName = name; this.errorMessage = error; }
        boolean hasError() { return errorMessage != null; }
    }

    @Override
    public void start(Stage primaryStage) {
        loadData();
        loadGeographicData();

        planner = new RoutePlannerComparison(roadNetwork, attractionMapper);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(5, 10, 5, 10));

        // UI input area
        GridPane inputGrid = new GridPane();
        inputGrid.setHgap(10);
        inputGrid.setVgap(3);
        startCityField = new TextField("New York NY");
        endCityField = new TextField("Los Angeles CA");
        attractionsField = new TextField("Hollywood Sign");
        planButton = new Button("Plan Route");
        resetButton = new Button("Reset View");

        inputGrid.add(new Label("Start:"), 0, 0); inputGrid.add(startCityField, 1, 0);
        inputGrid.add(new Label("End:"), 0, 1); inputGrid.add(endCityField, 1, 1);
        inputGrid.add(new Label("Via (comma-separated):"), 0, 2); inputGrid.add(attractionsField, 1, 2);
        inputGrid.add(resetButton, 0, 3);
        inputGrid.add(planButton, 1, 3);
        root.setTop(inputGrid);

        // Map display area
        graphPane = new Pane();
        graphPane.setPrefSize(800, 600); // the size of the map graph

        try {
            usMapImage = new Image(getClass().getResourceAsStream(mapImagePath));
            BackgroundImage backgroundImage = new BackgroundImage(
                    usMapImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.DEFAULT,
                    new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, true, false) // 保持比例填充
            );
            graphPane.setBackground(new Background(backgroundImage));
        } catch (Exception e) {
            System.err.println("Error: Map image not found, Path:" + mapImagePath);
            graphPane.setStyle("-fx-background-color: #cccccc; -fx-border-color: red;");
            graphPane.getChildren().add(new Text(50, 50, "Map image not found, please check the path. \n" + mapImagePath));
        }
        root.setCenter(new ScrollPane(graphPane));

        // Result output area
        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPrefHeight(105);
        root.setBottom(resultArea);

        // Plan button event: calculate and display route
        planButton.setOnAction(event -> {
            resultArea.clear();
            String startInput = startCityField.getText().trim();
            String endInput = endCityField.getText().trim();
            String viaText = attractionsField.getText().trim();
            List<String> errors = new ArrayList<>();

            CityResolutionResult startResult = resolveCityName(startInput);
            if (startResult.hasError()) errors.add("start city: " + startResult.errorMessage);
            String resolvedStartCity = startResult.resolvedName;

            CityResolutionResult endResult = resolveCityName(endInput);
            if (endResult.hasError()) errors.add("end city: " + endResult.errorMessage);
            String resolvedEndCity = endResult.resolvedName;

            errors.addAll(validateAttractions(viaText));

            if (!errors.isEmpty()) {
                resultArea.setText("Input Error:\n- " + String.join("\n- ", errors));
                drawGraph(null, null, null);
            } else {
                List<String> attractions = parseAttractions(viaText);
                List<String> path = planner.route(resolvedStartCity, resolvedEndCity, attractions);
                double distance = planner.getLastCalculatedOptimalDistance();

                resultArea.appendText("Start: " + resolvedStartCity + "\n");
                resultArea.appendText("End: " + resolvedEndCity + "\n");
                resultArea.appendText("Intermediate: " + (attractions.isEmpty() ? "无" : attractions) + "\n\n");

                if (path != null && distance >= 0) {
                    resultArea.appendText("Route plan: " + path + "\n");
                    resultArea.appendText(String.format("Distance: %.1f miles%n", distance));
                    drawGraph(path, resolvedStartCity, resolvedEndCity);
                } else {
                    resultArea.appendText("Route plan: Not found or path not reachable\n");
                    resultArea.appendText("distance: N/A\n");
                    drawGraph(null, null, null);
                }
            }
        });

        // Reset button event: reset all fields and clear map
        resetButton.setOnAction(event -> {
            startCityField.setText("New York NY");
            endCityField.setText("Los Angeles CA");
            attractionsField.clear();
            resultArea.clear();
            drawGraph(null, null, null);
        });

        Scene scene = new Scene(root, 950, 750);
        primaryStage.setTitle("Route Planner - US Map");
        primaryStage.setScene(scene);
        primaryStage.show();

        preparePixelCoordinates();
        drawGraph(null, null, null);
    }

    // Load city geographic coordinates from CSV
    private void loadGeographicData() {
        cityGeographicCoordinates = new HashMap<>();
        String cityCoordsPath = "/resources/city_coordinates.csv"; // path of cities coordinates

        try (BufferedReader reader = new BufferedReader(
            new java.io.InputStreamReader(
                getClass().getResourceAsStream(cityCoordsPath), "UTF-8"))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 4) { // CityName,StateAbbr,Latitude,Longitude
                    String cityName = parts[0].trim();
                    String stateAbbr = parts[1].trim();
                    String fullCityName = cityName + " " + stateAbbr;
                    try {
                        double lat = Double.parseDouble(parts[2].trim());
                        double lon = Double.parseDouble(parts[3].trim());
                        cityGeographicCoordinates.put(fullCityName, new Point2DGeographic(lat, lon));
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: wrong number format when parsing geographic coordinates, skip this line: " + line + " - " + e.getMessage());
                    }
                } else {
                    System.err.println("Warning: Wrong format of the geographic coordinates file (expect 4 columns), skip this line. " + line);
                }
            }
            System.out.println("from " + cityCoordsPath + " loaded " + cityGeographicCoordinates.size() + " cities coordinates");
        } catch (FileNotFoundException e) {
            System.err.println("Error: City geographic coordinates file not found. " + cityCoordsPath);
        } catch (IOException e) {
            System.err.println("Error: Failed to read city geographic coordinates file. " + e.getMessage());
        }
        if (cityGeographicCoordinates.isEmpty() && roadNetwork != null && !roadNetwork.getCities().isEmpty()) {
            System.err.println("Warning: The city's geographic coordinates data is empty. The city will not be positioned correctly on the map");
        }
    }

    // Prepare pixel coordinates for each city based on geographic data
    private void preparePixelCoordinates() {
        cityPixelCoordinates = new HashMap<>();
        if (cityNodes == null) cityNodes = new HashMap<>();
        cityNodes.clear();

        if (usMapImage == null || cityGeographicCoordinates == null || cityGeographicCoordinates.isEmpty()) {
            System.out.println("Map image or geographic coordinates not available, skip pixel coordinates preparation.");
            return;
        }

        double paneWidth = graphPane.getPrefWidth();
        double paneHeight = graphPane.getPrefHeight();

        for (String cityName : roadNetwork.getCities()) {
            Point2DGeographic geoCoords = cityGeographicCoordinates.get(cityName);
            if (geoCoords != null) {
                double x = (geoCoords.longitude - mapMinLongitude) / (mapMaxLongitude - mapMinLongitude) * paneWidth;
                double y = (1 - (geoCoords.latitude - mapMinLatitude) / (mapMaxLatitude - mapMinLatitude)) * paneHeight;
                cityPixelCoordinates.put(cityName, new Point2D(x, y));
            } else {
                System.err.println("Warning: Didn't find " + cityName + "'s coordinate");
            }
        }
        System.out.println("coordinates are drawn, already drawn: " + cityPixelCoordinates.size());
    }

    // Draw state labels on the map
    private void drawMapBaseElements() {
        if (usMapImage == null) return;
        for (StateLabelData sld : stateLabelDataList) {
            Text stateText = new Text(sld.x, sld.y, sld.abbr);
            stateText.setStyle("-fx-font-weight: bold; -fx-fill: black;");
            graphPane.getChildren().add(stateText);
        }
    }

    // Draw the graph and highlight the planned route if provided
    private void drawGraph(List<String> highlightPath, String startCityName, String endCityName) {
        graphPane.getChildren().clear();
        drawMapBaseElements();

        if (cityPixelCoordinates == null || cityPixelCoordinates.isEmpty()) {
            if (usMapImage != null) {
                Text missingCoordsMsg = new Text(50, 80, "data unloaded or not found。\n can not draw the route plan.");
                missingCoordsMsg.setFill(Color.RED);
                graphPane.getChildren().add(missingCoordsMsg);
            }
            System.out.println("can not draw the route plan：coordinates inapplicable.");
            return;
        }
        if (cityNodes == null) cityNodes = new HashMap<>();
        cityNodes.clear();

        Color defaultEdgeColor = Color.rgb(100, 100, 100, 0.6);
        Color highlightEdgeColor = Color.rgb(255, 50, 50);
        double defaultEdgeWidth = 1.5;
        double highlightEdgeWidth = 3.0;
        Color defaultNodeColor = Color.STEELBLUE;
        Color startNodeColor = Color.GREENYELLOW;
        Color endNodeColor = Color.ORANGERED;
        Color intermediateNodeColor = Color.GOLD;
        double defaultNodeRadius = 5;
        double highlightNodeRadius = 7;

        Set<String> highlightPathCities = (highlightPath != null) ? new HashSet<>(highlightPath) : Collections.emptySet();
        Set<String> highlightPathEdges = new HashSet<>();
        Map<String, Double> highlightEdgeDistances = new HashMap<>();

        // Highlight the edges in the planned route
        if (highlightPath != null && highlightPath.size() > 1) {
            for (int i = 0; i < highlightPath.size() - 1; i++) {
                String u = highlightPath.get(i);
                String v = highlightPath.get(i + 1);
                String edgeKey = u.compareTo(v) < 0 ? u + "|" + v : v + "|" + u;
                highlightPathEdges.add(edgeKey);
                double dist = getDirectDistance(u, v);
                if (dist >= 0) {
                    highlightEdgeDistances.put(edgeKey, dist);
                }
            }
        }

        Set<String> drawnEdges = new HashSet<>();
        if (roadNetwork != null && roadNetwork.getCities() != null) {
            boolean isRouteCurrentlyDisplayed = (highlightPath != null && !highlightPath.isEmpty());

            // Draw all edges (roads) between cities
            for (String cityFrom : roadNetwork.getCities()) {
                Point2D startPoint = cityPixelCoordinates.get(cityFrom);
                if (startPoint == null || roadNetwork.getNeighbors(cityFrom) == null) continue;

                for (Graph.Edge edge : roadNetwork.getNeighbors(cityFrom)) {
                    String cityTo = edge.targetCity();
                    Point2D endPoint = cityPixelCoordinates.get(cityTo);
                    if (endPoint == null) continue;

                    String edgeKey = cityFrom.compareTo(cityTo) < 0 ? cityFrom + "|" + cityTo : cityTo + "|" + cityFrom;
                    if (drawnEdges.add(edgeKey)) {
                        boolean isThisEdgeHighlighted = highlightPathEdges.contains(edgeKey);

                        if (isThisEdgeHighlighted) {
                            Line line = new Line(startPoint.getX(), startPoint.getY(), endPoint.getX(), endPoint.getY());
                            line.setStroke(highlightEdgeColor);
                            line.setStrokeWidth(highlightEdgeWidth);

                            Double distance = highlightEdgeDistances.get(edgeKey);
                            if (distance != null && distance >= 0) {
                                Tooltip segmentTooltip = new Tooltip(String.format("distance: %.1f miles", distance));
                                segmentTooltip.setStyle("-fx-font-size: 20px; -fx-background-color: rgba(255,255,220,0.85); -fx-text-fill: black; -fx-padding: 5px;");
                                Tooltip.install(line, segmentTooltip);
                            }
                            graphPane.getChildren().add(line);
                        } else {
                            if (!isRouteCurrentlyDisplayed) {
                                Line line = new Line(startPoint.getX(), startPoint.getY(), endPoint.getX(), endPoint.getY());
                                line.setStroke(defaultEdgeColor);
                                line.setStrokeWidth(defaultEdgeWidth);
                                graphPane.getChildren().add(line);
                            }
                        }
                    }
                }
            }

            // Draw all city nodes
            for (String city : roadNetwork.getCities()) {
                Point2D point = cityPixelCoordinates.get(city);
                if (point == null) continue;

                Circle circle = new Circle(point.getX(), point.getY(), defaultNodeRadius);
                boolean isOnPath = highlightPathCities.contains(city);
                if (city.equals(startCityName)) {
                    circle.setFill(startNodeColor); circle.setRadius(highlightNodeRadius);
                } else if (city.equals(endCityName)) {
                    circle.setFill(endNodeColor); circle.setRadius(highlightNodeRadius);
                } else if (isOnPath) {
                    circle.setFill(intermediateNodeColor); circle.setRadius(highlightNodeRadius);
                } else {
                    circle.setFill(defaultNodeColor); circle.setRadius(defaultNodeRadius);
                }
                circle.setStroke(Color.BLACK); circle.setStrokeWidth(0.5);

                Text text = new Text(point.getX() + 8, point.getY() + 4, city);
                text.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-fill: black;");

                graphPane.getChildren().addAll(circle, text);
                cityNodes.put(city, circle);
            }
        }
        System.out.println("finished update（if applicable）。");
    }

    // Load road and attraction data from CSV files
    private void loadData() {
        roadNetwork = new Graph();
        attractionMapper = new AttractionMapper();
        String roadsCsvPath = "/resources/roads.csv";
        String attractionsCsvPath = "/resources/attractions.csv";
        roadNetwork.loadRoads(roadsCsvPath);
        attractionMapper.loadAttractions(attractionsCsvPath);
        System.out.println("finished loaded");
    }

    // Get the direct distance between two cities if connected
    private double getDirectDistance(String city1, String city2) {
        if (roadNetwork == null || city1 == null || city2 == null) return -1.0;
        List<Graph.Edge> neighbors = roadNetwork.getNeighbors(city1);
        if (neighbors != null) {
            for (Graph.Edge edge : neighbors) {
                if (edge.targetCity().equals(city2)) return edge.distance();
            }
        }
        neighbors = roadNetwork.getNeighbors(city2);
        if (neighbors != null) {
            for (Graph.Edge edge : neighbors) {
                if (edge.targetCity().equals(city1)) return edge.distance();
            }
        }
        return -1.0;
    }

    // Parse attractions from input text
    private List<String> parseAttractions(String attractionsText) {
        if (attractionsText == null || attractionsText.trim().isEmpty()) { return Collections.emptyList(); }
        return Arrays.stream(attractionsText.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // Validate if attractions exist and can be mapped
    private List<String> validateAttractions(String attractionsText) {
        List<String> errorMessages = new ArrayList<>();
        Set<String> knownCitiesInRoadNetwork = roadNetwork.getCities();
        if (attractionsText != null && !attractionsText.isEmpty()) {
            String[] attractionNames = attractionsText.split(",");
            for (String name : attractionNames) {
                String trimmedName = name.trim();
                if (trimmedName.isEmpty()) continue;
                String cityForAttraction = attractionMapper.getCityForAttraction(trimmedName);
                if (cityForAttraction == null) {
                    errorMessages.add("not found intermediate: '" + trimmedName + "'");
                } else if (!knownCitiesInRoadNetwork.contains(cityForAttraction)) {
                    errorMessages.add("intermediate '" + trimmedName + "' 's city ('" + cityForAttraction + "') not found，shall not be passed");
                } else if (cityGeographicCoordinates != null && !cityGeographicCoordinates.containsKey(cityForAttraction)){
                    errorMessages.add("intermediate'" + trimmedName + "' 's city ('" + cityForAttraction + "') has no coordinate，can not be drawn on the map");
                }
            }
        }
        return errorMessages;
    }

    // Try to resolve city name or abbreviation to a full city name
    private CityResolutionResult resolveCityName(String input) {
        if (input == null || input.isEmpty()) { return new CityResolutionResult(null, "input can not be empty."); }
        Set<String> knownCities = roadNetwork.getCities();
        if (knownCities.contains(input)) { // Directly match "City ST"
            if (cityGeographicCoordinates != null && !cityGeographicCoordinates.containsKey(input)) {
                return new CityResolutionResult(null, " 'city '" + input + "' is found in the map，but has no coordinates，can not be drawn.");
            }
            return new CityResolutionResult(input, null);
        }

        if (input.matches("[A-Z]{2}")) {
            String stateAbbr = input;
            List<String> matches = knownCities.stream()
                    .filter(city -> city.endsWith(" " + stateAbbr))
                    .filter(city -> cityGeographicCoordinates != null && cityGeographicCoordinates.containsKey(city))
                    .collect(Collectors.toList());
            if (matches.size() == 1) {
                return new CityResolutionResult(matches.get(0), null);
            } else if (matches.size() > 1) {
                return new CityResolutionResult(null, "state abbreviation '" + input + "' Corresponds to multiple cities with coordinates. " + matches + "。Please enter the full 'City ST' name.");
            } else {
                List<String> allStateMatches = knownCities.stream().filter(city -> city.endsWith(" " + stateAbbr)).collect(Collectors.toList());
                if (!allStateMatches.isEmpty() && matches.isEmpty()){ // 有城市但都没坐标
                    return new CityResolutionResult(null, "state '" + input + "' Cities found within, but none with geographic coordinates. " + allStateMatches);
                }
                return new CityResolutionResult(null, "didn't found the abbreviation of the state '" + input + "' Corresponding cities with coordinates.");
            }
        }
        return new CityResolutionResult(null, "City or abbreviation unrecognizable, or missing coordinates. '" + input + "'");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

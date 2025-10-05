import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class SortingPerformanceTester {

    private static final int NUMBER_OF_RUNS = 10;

    public static void main(String[] args) {
        // Configuration
        String[] filePaths = {
                "/resources/1000places_sorted.csv",
                "/resources/1000places_random.csv",
                "/resources/10000places_sorted.csv",
                "/resources/10000places_random.csv"
        };

        System.out.println("Starting Sorting Performance Test...");
        System.out.println("Each algorithm will be run " + NUMBER_OF_RUNS + " times per dataset, and the average time will be displayed.");
        System.out.println("=======================================");

        for (String filePath : filePaths) {
            System.out.println("\nTesting Dataset: " + Paths.get(filePath).getFileName());

            List<String> originalData = readPlaceNames(filePath);
            if (originalData == null) {
                System.out.println("  Skipping this dataset due to read error.");
                continue;
            }
            if (originalData.isEmpty()) {
                System.out.println("  Skipping this dataset because it is empty.");
                continue;
            }

            List<String> expectedSortedList = null;
            boolean needsVerification = filePath.contains("_random");
            if (needsVerification) {
                String sortedFilePath = filePath.replace("_random", "_sorted");
                expectedSortedList = readPlaceNames(sortedFilePath);
                if (expectedSortedList == null) {
                    System.err.println("  WARNING: Could not load " + Paths.get(sortedFilePath).getFileName() + " for verification.");
                }
            }

            // Test Insertion Sort
            try {
                long totalDurationInsertion = 0;
                boolean verificationFailedInsertion = false;
                for (int i = 0; i < NUMBER_OF_RUNS; i++) {
                    List<String> listToSort = new ArrayList<>(originalData);
                    long startTime = System.nanoTime();
                    insertionSort(listToSort);
                    long endTime = System.nanoTime();
                    totalDurationInsertion += (endTime - startTime);

                    // Verify result on first run if needed
                    if (i == 0 && needsVerification && expectedSortedList != null) {
                        if (!listToSort.equals(expectedSortedList)) {
                            System.err.println("  ERROR: Verification FAILED for Insertion Sort!");
                            verificationFailedInsertion = true;
                        }
                    }
                }
                double averageDurationMsInsertion = (totalDurationInsertion / (double) NUMBER_OF_RUNS) / 1_000_000.0;
                System.out.printf("  %-20s: Average %.3f ms%s%n", "Insertion Sort", averageDurationMsInsertion, verificationFailedInsertion ? " (Verification FAILED)" : "");

            } catch (Exception e) {
                System.err.printf("  %-20s: Error during execution - %s%n", "Insertion Sort", e.getMessage());
            }

            // Test Quick Sort
            try {
                long totalDurationQuick = 0;
                boolean verificationFailedQuick = false;
                boolean stackOverflowOccurred = false;

                for (int run = 0; run < NUMBER_OF_RUNS; run++) {
                    List<String> listToSort = new ArrayList<>(originalData);
                    long startTime = System.nanoTime();
                    try {
                        quickSort(listToSort);
                    } catch (StackOverflowError soe) {
                        if (run == 0) {
                            System.err.printf("  %-20s: StackOverflowError during execution!%n", "Quick Sort");
                            stackOverflowOccurred = true;
                        }
                        totalDurationQuick = -1;
                        break;
                    }
                    long endTime = System.nanoTime();
                    if(totalDurationQuick != -1) {
                        totalDurationQuick += (endTime - startTime);
                    }

                    // Verify result on first run if needed
                    if (run == 0 && needsVerification && expectedSortedList != null && !stackOverflowOccurred) {
                        if (!listToSort.equals(expectedSortedList)) {
                            System.err.println("  ERROR: Verification FAILED for Quick Sort!");
                            verificationFailedQuick = true;
                        }
                    }
                }

                if (!stackOverflowOccurred && totalDurationQuick != -1) {
                    double averageDurationMsQuick = (totalDurationQuick / (double) NUMBER_OF_RUNS) / 1_000_000.0;
                    System.out.printf("  %-20s: Average %.3f ms%s%n", "Quick Sort", averageDurationMsQuick, verificationFailedQuick ? " (Verification FAILED)" : "");
                } else if (totalDurationQuick == -1 && !stackOverflowOccurred) {
                    System.err.printf("  %-20s: Error during execution prevented timing.%n", "Quick Sort");
                }


            } catch (Exception e) {
                System.err.printf("  %-20s: Error during execution - %s%n", "Quick Sort", e.getMessage());
            }

            // Test Merge Sort
            try {
                long totalDurationMerge = 0;
                boolean verificationFailedMerge = false;
                for (int i = 0; i < NUMBER_OF_RUNS; i++) {
                    List<String> listToSort = new ArrayList<>(originalData);
                    long startTime = System.nanoTime();
                    mergeSort(listToSort);
                    long endTime = System.nanoTime();
                    totalDurationMerge += (endTime - startTime);

                    // Verify result on first run if needed
                    if (i == 0 && needsVerification && expectedSortedList != null) {
                        if (!listToSort.equals(expectedSortedList)) {
                            System.err.println("  ERROR: Verification FAILED for Merge Sort!");
                            verificationFailedMerge = true;
                        }
                    }
                }
                double averageDurationMsMerge = (totalDurationMerge / (double) NUMBER_OF_RUNS) / 1_000_000.0;
                System.out.printf("  %-20s: Average %.3f ms%s%n", "Merge Sort", averageDurationMsMerge, verificationFailedMerge ? " (Verification FAILED)" : "");

            } catch (Exception e) {
                System.err.printf("  %-20s: Error during execution - %s%n", "Merge Sort", e.getMessage());
            }
        }

        System.out.println("\n=======================================");
        System.out.println("Performance Test Finished.");
    }

    // Read place names from a resource file
    public static List<String> readPlaceNames(String resourcePath) {
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(
                    SortingPerformanceTester.class.getResourceAsStream(resourcePath), "UTF-8"))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            System.err.println("Error reading file: " + resourcePath);
            return null;
        } catch (Exception e) {
            System.err.println("An unexpected error occurred while reading file: " + resourcePath);
            return null;
        }
    }

    // Insertion Sort implementation
    public static void insertionSort(List<String> list) {
        if (list == null || list.size() < 2) {
            return;
        }
        for (int i = 1; i < list.size(); i++) {
            String current = list.get(i);
            int j = i - 1;
            while (j >= 0 && list.get(j).compareTo(current) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, current);
        }
    }

    // Quick Sort implementation
    public static void quickSort(List<String> list) {
        if (list == null || list.size() < 2) {
            return;
        }
        quickSortRecursive(list, 0, list.size() - 1);
    }

    private static void quickSortRecursive(List<String> list, int low, int high) {
        if (low < high) {
            int pivotIndex = partition(list, low, high);
            quickSortRecursive(list, low, pivotIndex - 1);
            quickSortRecursive(list, pivotIndex + 1, high);
        }
    }

    // Partition logic for Quick Sort
    private static int partition(List<String> list, int low, int high) {
        if (high <= low) {
            if (high == low + 1 && list.get(low).compareTo(list.get(high)) > 0) {
                swap(list, low, high);
            }
            return low;
        }

        int mid = low + (high - low) / 2;
        if (list.get(low).compareTo(list.get(mid)) > 0) swap(list, low, mid);
        if (list.get(low).compareTo(list.get(high)) > 0) swap(list, low, high);
        if (list.get(mid).compareTo(list.get(high)) > 0) swap(list, mid, high);
        swap(list, mid, high - 1);
        String pivot = list.get(high - 1);

        int i = low;
        int j = high - 1;

        while (true) {
            while (i < high -1 && list.get(++i).compareTo(pivot) < 0) {
            }

            while (j > low && list.get(--j).compareTo(pivot) > 0) {
            }

            if (i < j) {
                swap(list, i, j);
            } else {
                break;
            }
        }
        swap(list, i, high - 1);
        return i;
    }

    // Swap helper for sorting
    private static void swap(List<String> list, int i, int j) {
        String temp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, temp);
    }

    // Merge Sort implementation
    public static void mergeSort(List<String> list) {
        if (list == null || list.size() < 2) {
            return;
        }
        List<String> temp = new ArrayList<>(Collections.nCopies(list.size(), null));
        mergeSortRecursive(list, temp, 0, list.size() - 1);
    }

    private static void mergeSortRecursive(List<String> list, List<String> temp, int low, int high) {
        if (low < high) {
            int mid = low + (high - low) / 2;
            mergeSortRecursive(list, temp, low, mid);
            mergeSortRecursive(list, temp, mid + 1, high);
            merge(list, temp, low, mid, high);
        }
    }

    // Merge logic for Merge Sort
    private static void merge(List<String> list, List<String> temp, int low, int mid, int high) {
        for (int k = low; k <= high; k++) {
            temp.set(k, list.get(k));
        }
        int i = low;
        int j = mid + 1;
        int k = low;
        while (i <= mid && j <= high) {
            if (temp.get(i).compareTo(temp.get(j)) <= 0) {
                list.set(k++, temp.get(i++));
            } else {
                list.set(k++, temp.get(j++));
            }
        }
        while (i <= mid) {
            list.set(k++, temp.get(i++));
        }
    }
}

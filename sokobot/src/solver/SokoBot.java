package solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

public class SokoBot {
  private Zobrist zobrist;
  private int[][] patternDatabase;

  private class Zobrist {
    private final long[][][] table;
    private static final int PLAYER_INDEX = 0;
    private static final int BOX_INDEX = 1;
    public Zobrist(int height, int width) {
      table = new long[height][width][2];
      Random rand = new Random(12345);
      for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j++) {
          table[i][j][PLAYER_INDEX] = rand.nextLong();
          table[i][j][BOX_INDEX] = rand.nextLong();
        }
      }
    }
    public long computeHash(int playerRow, int playerCol, int[][] boxPositions) {
      long hash = 0;
      hash ^= table[playerRow][playerCol][PLAYER_INDEX];
      for (int[] boxPos : boxPositions) {
        hash ^= table[boxPos[0]][boxPos[1]][BOX_INDEX];
      }
      return hash;
    }
  }

  private class State implements Comparable<State> {
    int playerRow, playerCol;
    int[][] boxPositions;
    State parent;
    char move;
    int g, h, f;
    long zobristHash;
    private final char[][] mapData;
    private final List<int[]> goalPositions;
    private final boolean[][] deadSquares;

    public State(int pR, int pC, int[][] bP, char[][] mD, List<int[]> gP, boolean[][] dS) {
      playerRow = pR; playerCol = pC; boxPositions = bP; mapData = mD; goalPositions = gP;
      deadSquares = dS; parent = null; move = ' '; g = 0;
      h = calculateHeuristic(); f = this.g + this.h;
      Arrays.sort(this.boxPositions, (a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
      this.zobristHash = zobrist.computeHash(this.playerRow, this.playerCol, this.boxPositions);
    }

    public State(State parent, int newPR, int newPC, int[][] newBP, char m) {
      this(newPR, newPC, newBP, parent.mapData, parent.goalPositions, parent.deadSquares);
      this.parent = parent;
      this.move = m;
      this.g = parent.g + 1;
      this.f = this.g + this.h;
    }

    private int calculateHeuristic() {
    int totalPushDistance = 0;

    for (int[] boxPos : boxPositions) {
        int pdbVal = patternDatabase[boxPos[0]][boxPos[1]];
        totalPushDistance += (pdbVal == Integer.MAX_VALUE) ? 1000 : pdbVal; // Penalize unreachable tiles
    }

    int tieBreaker = Arrays.stream(boxPositions)
            .mapToInt(b -> b[0] * 100 + b[1])
            .sum();

    // Combine heuristic and tiebreaker like in your code
    return totalPushDistance * 10000 + tieBreaker;
  }

    public List<State> getSuccessors() {
      List<State> successors = new ArrayList<>();
      int[] dRow = {-1, 1, 0, 0};
      int[] dCol = {0, 0, -1, 1};
      char[] moves = {'u', 'd', 'l', 'r'};

      for (int i = 0; i < 4; i++) {
        int newPlayerRow = playerRow + dRow[i];
        int newPlayerCol = playerCol + dCol[i];
        if (mapData[newPlayerRow][newPlayerCol] == '#') continue;

        int boxIndex = getBoxIndexAt(newPlayerRow, newPlayerCol);
        if (boxIndex != -1) {
          int newBoxRow = newPlayerRow + dRow[i];
          int newBoxCol = newPlayerCol + dCol[i];
          if (mapData[newBoxRow][newBoxCol] != '#' && getBoxIndexAt(newBoxRow, newBoxCol) == -1) {
            if (!deadSquares[newBoxRow][newBoxCol]) {
              int[][] newBoxPositions = deepCopyBoxPositions();
              newBoxPositions[boxIndex][0] = newBoxRow;
              newBoxPositions[boxIndex][1] = newBoxCol;
              successors.add(new State(this, newPlayerRow, newPlayerCol, newBoxPositions, moves[i]));
            }
          }
        } else {
          successors.add(new State(this, newPlayerRow, newPlayerCol, this.boxPositions, moves[i]));
        }
      }
      return successors;
    }

    public boolean isGoalState() {
      for (int[] boxPos : boxPositions) {
        boolean onGoal = false;
        for (int[] goalPos : goalPositions) {
          if (boxPos[0] == goalPos[0] && boxPos[1] == goalPos[1]) {
            onGoal = true;
            break;
          }
        }
        if (!onGoal) return false;
      }
      return true;
    }

    private int getBoxIndexAt(int row, int col) {
      for (int i = 0; i < boxPositions.length; i++) {
        if (boxPositions[i][0] == row && boxPositions[i][1] == col) return i;
      }
      return -1;
    }

    private int[][] deepCopyBoxPositions() {
      int[][] newBoxPositions = new int[boxPositions.length][2];
      for (int i = 0; i < boxPositions.length; i++) {
        newBoxPositions[i][0] = boxPositions[i][0];
        newBoxPositions[i][1] = boxPositions[i][1];
      }
      return newBoxPositions;
    }

    @Override
    public int compareTo(State other) { return Integer.compare(this.f, other.f); }
    @Override
    public boolean equals(Object o) { return o instanceof State && this.zobristHash == ((State) o).zobristHash; }
    @Override
    public int hashCode() { return Objects.hash(zobristHash); }
  }


  public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {
    this.zobrist = new Zobrist(height, width);
    int playerRow = -1, playerCol = -1;
    List<int[]> boxPositionsList = new ArrayList<>();
    List<int[]> goalPositions = new ArrayList<>();

    for (int r = 0; r < height; r++) {
      for (int c = 0; c < width; c++) {
        if (itemsData[r][c] == '@') { playerRow = r; playerCol = c; }
        else if (itemsData[r][c] == '$') { boxPositionsList.add(new int[]{r, c}); }
        if (mapData[r][c] == '.') { goalPositions.add(new int[]{r, c}); }
      }
    }

    int[][] boxPositions = boxPositionsList.toArray(new int[0][]);
    boolean[][] deadSquares = precomputeDeadlocks(height, width, mapData, goalPositions);
    this.patternDatabase = buildPatternDatabase(height, width, mapData, goalPositions);

    State initialState = new State(playerRow, playerCol, boxPositions, mapData, goalPositions, deadSquares);
    PriorityQueue<State> openSet = new PriorityQueue<>();
    Set<Long> closedSet = new HashSet<>();

    openSet.add(initialState);
    closedSet.add(initialState.zobristHash);

    while (!openSet.isEmpty()) {
      State currentState = openSet.poll();
      if (currentState.isGoalState()) { return reconstructPath(currentState); }
      for (State successor : currentState.getSuccessors()) {
        if (closedSet.add(successor.zobristHash)) {
          openSet.add(successor);
        }
      }
    }

    return "No solution found";
  }

  private int[][] buildPatternDatabase(int height, int width, char[][] mapData, List<int[]> goalPositions) {
    int[][] pdb = new int[height][width];
    for(int[] row : pdb) Arrays.fill(row, Integer.MAX_VALUE);
    Queue<int[]> queue = new LinkedList<>();
    for(int[] goal : goalPositions){
      pdb[goal[0]][goal[1]] = 0;
      queue.add(new int[]{goal[0], goal[1]});
    }
    int[] dRow = {-1, 1, 0, 0};
    int[] dCol = {0, 0, -1, 1};
    while(!queue.isEmpty()){
      int[] curr = queue.poll();
      for(int i=0; i<4; i++){
        int prevBoxR = curr[0] - dRow[i], prevBoxC = curr[1] - dCol[i];
        int pushFromR = curr[0] + dRow[i], pushFromC = curr[1] + dCol[i];
        if(prevBoxR > 0 && prevBoxR < height-1 && prevBoxC > 0 && prevBoxC < width-1 &&
                mapData[prevBoxR][prevBoxC] != '#' && mapData[pushFromR][pushFromC] != '#'){
          if(pdb[prevBoxR][prevBoxC] > pdb[curr[0]][curr[1]] + 1){
            pdb[prevBoxR][prevBoxC] = pdb[curr[0]][curr[1]] + 1;
            queue.add(new int[]{prevBoxR, prevBoxC});
          }
        }
      }
    }
    return pdb;
  }

  private boolean[][] precomputeDeadlocks(int height, int width, char[][] mapData, List<int[]> goalPositions) {
    boolean[][] deadSquares = new boolean[height][width];
    for (int r = 1; r < height - 1; r++) {
      for (int c = 1; c < width - 1; c++) {
        if (mapData[r][c] == '#') continue;
        boolean isGoal = false;
        for (int[] goalPos : goalPositions) {
          if (goalPos[0] == r && goalPos[1] == c) {
            isGoal = true;
            break;
          }
        }
        if (isGoal) continue;
        boolean wallUp = mapData[r - 1][c] == '#', wallDown = mapData[r + 1][c] == '#';
        boolean wallLeft = mapData[r][c - 1] == '#', wallRight = mapData[r][c + 1] == '#';
        if ((wallUp || wallDown) && (wallLeft || wallRight)) deadSquares[r][c] = true;
      }
    }
    return deadSquares;
  }

  private String reconstructPath(State goalState) {
    StringBuilder path = new StringBuilder();
    State currentState = goalState;
    while (currentState.parent != null) {
      path.append(currentState.move);
      currentState = currentState.parent;
    }
    return path.reverse().toString();
  }
}


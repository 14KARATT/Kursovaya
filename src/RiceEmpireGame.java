import java.io.*;
import java.util.*;

import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.SeriesMarkers;

public class RiceEmpireGame implements Serializable {

    static class Position implements Serializable {
        int x, y;
        Position(int x, int y) { this.x = x; this.y = y; }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Position)) return false;
            Position p = (Position) o;
            return x == p.x && y == p.y;
        }
        @Override
        public int hashCode() { return x * 31 + y; }
    }

    static class Player implements Serializable {
        int water, rice, peasants, houses;
        Set<Position> controlled = new HashSet<>();
        List<Integer> dayHist = new ArrayList<>();
        List<Integer> waterHist = new ArrayList<>();
        List<Integer> riceHist = new ArrayList<>();
        List<Integer> peasantsHist = new ArrayList<>();
        List<Integer> housesHist = new ArrayList<>();
        List<Integer> territoryHist = new ArrayList<>();
        boolean irrigated = false;

        Player(int w, int r, int p, int h, Position start) {
            water = w; rice = r; peasants = p; houses = h;
            controlled.add(start);
            recordHistory(0);
        }

        void recordHistory(int day) {
            dayHist.add(day);
            waterHist.add(water);
            riceHist.add(rice);
            peasantsHist.add(peasants);
            housesHist.add(houses);
            territoryHist.add(controlled.size());
        }
    }

    static class Game implements Serializable {
        Player human, ai;
        int[][] map;
        int day = 0;
        final int SIZE = 5;
        final int WIN_TERRITORY = 13;

        Game() {
            map = new int[][] {
                    {3, 3, 4, 5, 6},
                    {3, 3, 4, 5, 5},
                    {2, 2, 3, 4, 4},
                    {1, 1, 2, 3, 3},
                    {1, 1, 2, 3, 3}
            };

            human = new Player(20, 10, 3, 1, new Position(4, 4));
            ai    = new Player(20, 10, 3, 1, new Position(0, 0));
        }

        void humanTurn(Scanner sc) {
            printStats("=== ВАШ ХОД (день " + (day + 1) + ") ===");
            System.out.println("1. Набрать воды (+12)");
            System.out.println("2. Полить рис (8 воды → x2 урожай сегодня)");
            System.out.println("3. Освоить новую территорию");
            System.out.println("4. Построить дом (15 рис + 8 воды + 1 кр)");
            System.out.println("5. Сохранить игру");
            System.out.println("6. Закончить игру и экспорт (CSV + графики)");

            System.out.print("Ваш выбор: ");
            int choice;
            try {
                choice = sc.nextInt();
                sc.nextLine();
            } catch (InputMismatchException e) {
                sc.nextLine();
                System.out.println("[ERR] Введите число от 1 до 6");
                return;
            }

            switch (choice) {
                case 1:
                    human.water += 12;
                    System.out.println("[OK] Вода набрана (+12)");
                    break;
                case 2:
                    if (human.water >= 8) {
                        human.water -= 8;
                        human.irrigated = true;
                        System.out.println("[OK] Поля политы (x2 урожай сегодня)");
                    } else {
                        System.out.println("[ERR] Не хватает воды (нужно 8)");
                    }
                    break;
                case 3:
                    conquerTerritory(human, sc);
                    break;
                case 4:
                    buildHouse(human);
                    break;
                case 5:
                    saveGame();
                    break;
                case 6:
                    exportAll();
                    System.exit(0);
                    break;
                default:
                    System.out.println("[ERR] Неверный выбор, попробуйте снова");
                    break;
            }
        }

        void conquerTerritory(Player player, Scanner sc) {
            List<Position> possible = getAdjacentUncontrolled(player);
            if (possible.isEmpty()) {
                System.out.println("[ERR] Нет соседних территорий!");
                return;
            }
            System.out.println("Доступные:");
            for (int i = 0; i < possible.size(); i++) {
                Position pos = possible.get(i);
                int cost = map[pos.x][pos.y];
                System.out.printf("%d. (%d,%d) - %d кр (урожай: %d/день)%n", i + 1, pos.x, pos.y, cost, cost);
            }
            System.out.print("Выбор: ");
            int idx;
            try {
                idx = sc.nextInt() - 1;
                sc.nextLine();
            } catch (InputMismatchException e) {
                sc.nextLine();
                System.out.println("[ERR] Неверный ввод!");
                return;
            }
            if (idx < 0 || idx >= possible.size()) {
                System.out.println("[ERR] Неверный выбор!");
                return;
            }
            Position target = possible.get(idx);
            int cost = map[target.x][target.y];
            if (player.peasants >= cost) {
                player.peasants -= cost;
                player.controlled.add(target);
                System.out.println("[OK] Территория захвачена! (+ " + cost + " риса/день)");
            } else {
                System.out.println("[ERR] Не хватает крестьян (нужно " + cost + ")");
            }
        }

        void buildHouse(Player player) {
            if (player.rice >= 15 && player.water >= 8 && player.peasants >= 1) {
                player.rice -= 15;
                player.water -= 8;
                player.peasants -= 1;
                player.houses++;
                System.out.println("[OK] Дом построен!");
            } else {
                System.out.println("[ERR] Не хватает: рис≥15, вода≥8, кр≥1");
            }
        }

        void aiTurn() {
            String action = "";
            List<Position> possible = getAdjacentUncontrolled(ai);
            int oppTerr = human.controlled.size();

            if (!possible.isEmpty() && ai.controlled.size() <= oppTerr + 1) {
                Position best = null;
                int minCost = Integer.MAX_VALUE;
                for (Position pos : possible) {
                    int cost = map[pos.x][pos.y];
                    if (ai.peasants >= cost + 1 && cost < minCost) {
                        minCost = cost;
                        best = pos;
                    }
                }
                if (best != null) {
                    action = "осваивает (" + minCost + " кр)";
                    ai.peasants -= minCost;
                    ai.controlled.add(best);
                } else {
                    action = "готовится к захвату";
                }
            } else if (ai.rice >= 15 && ai.water >= 8 && ai.peasants >= 3) {
                action = "строит дом";
                buildHouse(ai);
            } else if (ai.water < 10) {
                action = "набирает воду";
                ai.water += 12;
            } else if (ai.water >= 8) {
                action = "поливает";
                ai.water -= 8;
                ai.irrigated = true;
            } else {
                action = "набирает воду";
                ai.water += 12;
            }

            System.out.println("AI > " + action);
        }

        List<Position> getAdjacentUncontrolled(Player player) {
            List<Position> list = new ArrayList<>();
            int[] dx = {-1, 0, 1, 0};
            int[] dy = {0, 1, 0, -1};
            for (Position cur : player.controlled) {
                for (int d = 0; d < 4; d++) {
                    int nx = cur.x + dx[d];
                    int ny = cur.y + dy[d];
                    if (nx < 0 || nx >= SIZE || ny < 0 || ny >= SIZE) continue;
                    Position np = new Position(nx, ny);
                    if (!human.controlled.contains(np) && !ai.controlled.contains(np)) {
                        list.add(np);
                    }
                }
            }
            return new ArrayList<>(new LinkedHashSet<>(list));
        }

        void endDay() {
            int humanHarvest = processPlayer(human);
            int aiHarvest = processPlayer(ai);

            human.recordHistory(day + 1);
            ai.recordHistory(day + 1);
            day++;

            printStats("=== КОНЕЦ ДНЯ " + day + " ===");
            System.out.printf("  Урожай: Вы +%d | AI +%d | Новые кр: +%d / +%d%n",
                    humanHarvest, aiHarvest, human.houses, ai.houses);
            checkWin();
        }

        int processPlayer(Player player) {
            int base = 0;
            for (Position pos : player.controlled) {
                base += map[pos.x][pos.y];
            }
            int prod = player.irrigated ? base * 2 : base;
            player.rice += prod;
            player.peasants += player.houses;
            player.irrigated = false;
            return prod;
        }

        void checkWin() {
            if (human.controlled.size() >= WIN_TERRITORY) {
                System.out.println("*** ТВОЯ ПОБЕДА! ***");
                exportAll();
                System.exit(0);
            }
            if (ai.controlled.size() >= WIN_TERRITORY) {
                System.out.println("*** ПОБЕДИЛ AI! ***");
                exportAll();
                System.exit(0);
            }
        }

        void exportCharts() {
            try {
                String dirName = "charts";
                File chartDir = new File(dirName);

                // === ОЧИЩАЕМ ПАПКУ ОТ СТАРЫХ ГРАФИКОВ ===
                if (chartDir.exists()) {
                    for (File file : chartDir.listFiles()) {
                        if (file.getName().toLowerCase().endsWith(".png")) {
                            file.delete();
                        }
                    }
                } else {
                    chartDir.mkdirs();
                }

                XYChart terr = new XYChartBuilder().width(1100).height(650)
                        .title("Захваченная территория").xAxisTitle("День").yAxisTitle("Территорий").build();
                terr.addSeries("Вы", human.dayHist, human.territoryHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                terr.addSeries("AI", ai.dayHist, ai.territoryHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                BitmapEncoder.saveBitmap(terr, dirName + "/01_territory", BitmapEncoder.BitmapFormat.PNG);

                XYChart rice = new XYChartBuilder().width(1100).height(650)
                        .title("Запасы риса").xAxisTitle("День").yAxisTitle("Рис").build();
                rice.addSeries("Вы", human.dayHist, human.riceHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                rice.addSeries("AI", ai.dayHist, ai.riceHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                BitmapEncoder.saveBitmap(rice, dirName + "/02_rice", BitmapEncoder.BitmapFormat.PNG);

                XYChart water = new XYChartBuilder().width(1100).height(650)
                        .title("Вода").xAxisTitle("День").yAxisTitle("Вода").build();
                water.addSeries("Вы", human.dayHist, human.waterHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                water.addSeries("AI", ai.dayHist, ai.waterHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                BitmapEncoder.saveBitmap(water, dirName + "/03_water", BitmapEncoder.BitmapFormat.PNG);

                XYChart peasants = new XYChartBuilder().width(1100).height(650)
                        .title("Крестьяне").xAxisTitle("День").yAxisTitle("Крестьяне").build();
                peasants.addSeries("Вы", human.dayHist, human.peasantsHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                peasants.addSeries("AI", ai.dayHist, ai.peasantsHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                BitmapEncoder.saveBitmap(peasants, dirName + "/04_peasants", BitmapEncoder.BitmapFormat.PNG);

                XYChart houses = new XYChartBuilder().width(1100).height(650)
                        .title("Дома").xAxisTitle("День").yAxisTitle("Дома").build();
                houses.addSeries("Вы", human.dayHist, human.housesHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                houses.addSeries("AI", ai.dayHist, ai.housesHist).setMarker(SeriesMarkers.NONE).setLineWidth(3);
                BitmapEncoder.saveBitmap(houses, dirName + "/05_houses", BitmapEncoder.BitmapFormat.PNG);

                System.out.println("\n[OK] Графики успешно сохранены и обновлены!");
                System.out.println("Папка: " + chartDir.getAbsolutePath());
                System.out.println("Файлы:");
                System.out.println("   → 01_territory.png");
                System.out.println("   → 02_rice.png");
                System.out.println("   → 03_water.png");
                System.out.println("   → 04_peasants.png");
                System.out.println("   → 05_houses.png");

            } catch (IOException e) {
                System.out.println("[ERR] Не удалось сохранить графики: " + e.getMessage());
            }
        }

        void exportAll() {
            exportCSV();
            exportCharts();
        }


        void exportCSV() {
            try (PrintWriter pw = new PrintWriter("rice_empire_history.csv")) {
                pw.println("Day,HumanWater,HumanRice,HumanPeasants,HumanHouses,HumanTerr,AIWater,AIRice,AIPeasants,AIHouses,AITerr");
                int len = human.dayHist.size();
                for (int i = 0; i < len; i++) {
                    pw.printf("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                            human.dayHist.get(i),
                            human.waterHist.get(i), human.riceHist.get(i), human.peasantsHist.get(i),
                            human.housesHist.get(i), human.territoryHist.get(i),
                            ai.waterHist.get(i), ai.riceHist.get(i), ai.peasantsHist.get(i),
                            ai.housesHist.get(i), ai.territoryHist.get(i));
                }
                System.out.println("CSV: rice_empire_history.csv создан");
            } catch (IOException e) {
                System.out.println("Ошибка CSV");
            }
        }

        void printStats(String title) {
            System.out.println("\n" + title);
            System.out.printf("День: %d | Терр Вы:%d AI:%d / %d%n",
                    day, human.controlled.size(), ai.controlled.size(), SIZE * SIZE);
            System.out.printf("Вы:    Вода=%d Рис=%d Кр=%d Дома=%d%n",
                    human.water, human.rice, human.peasants, human.houses);
            System.out.printf("AI:    Вода=%d Рис=%d Кр=%d Дома=%d%n",
                    ai.water, ai.rice, ai.peasants, ai.houses);
        }

        void saveGame() {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("savegame.dat"))) {
                oos.writeObject(this);
                System.out.println("Save: Игра сохранена!");
            } catch (IOException e) {
                System.out.println("Ошибка сохранения: " + e.getMessage());
            }
        }

        static Game loadGame() {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("savegame.dat"))) {
                return (Game) ois.readObject();
            } catch (Exception e) {
                System.out.println("Сохранение не найдено. Новая игра.");
                return new Game();
            }
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Game game;

        System.out.println("Загрузить сохранение? (y/n)");
        if (sc.nextLine().trim().equalsIgnoreCase("y")) {
            game = Game.loadGame();
        } else {
            game = new Game();
        }

        System.out.println("--> Игра началась! Цель: захватить 13 территорий первым.");
        System.out.println("Совет: вода → полив → захват → дом\n");

        while (true) {
            game.humanTurn(sc);
            game.aiTurn();
            game.endDay();
        }
    }
}
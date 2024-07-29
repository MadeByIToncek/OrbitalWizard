package space.itoncek.orbitalwizard;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class OrbitalWizard {
	public TreeMap<Double,ArrayList<Point2D>> data = new TreeMap<>();
	public TreeMap<LocalDateTime,Double> flareData = new TreeMap<>();
	public HashMap<Double, LocalDateTime> dateLookup = new HashMap<>();
	public OrbitalWizard(String path) throws IOException {
		long start = System.currentTimeMillis();
		for (File dir : Objects.requireNonNull(new File(path).listFiles())) {
			if (dir.isDirectory()) {
				for (File eph : Objects.requireNonNull(dir.listFiles())) {
					boolean reading = false;
					for (String line : Files.readAllLines(eph.toPath(), Charset.defaultCharset())) {
						if (!reading) {
							if (line.contains("$$SOE")) {
								reading = true;
							}
						} else {
							if (line.contains("$$EOE")) break;
							else {
								//System.out.println(line);
								String[] elements = line.split("\\r?,");
								double date = Double.parseDouble(elements[0].trim().strip());

								if (!data.containsKey(date)) {
									data.put(date, new ArrayList<>(20));
								}

								if (!dateLookup.containsKey(date)) {
									String stdate = elements[1].trim().strip().replace("Sep", "Sept");
									dateLookup.put(date, LocalDateTime.parse(stdate, DateTimeFormatter.ofPattern("'A.D. 'yyyy-MMM-dd HH:mm:ss.SSSS")));
								}

								data.get(date).add(new Point2D(eph.getName(), Double.parseDouble(elements[2].trim().strip())/1e8, Double.parseDouble(elements[3].trim().strip())/1e8));
							}
						}
					}
				}
			}
		}
		JSONArray array = new JSONArray(IOUtils.readLines(URI.create("https://services.swpc.noaa.gov/json/goes/primary/xrays-7-day.json").toURL().openStream(), Charset.defaultCharset()).stream().reduce((a,b) -> a+b).get());
		for (int i = 0; i < array.length(); i++) {
			JSONObject obj = array.getJSONObject(i);
			LocalDateTime dateTime = LocalDateTime.parse(obj.getString("time_tag").substring(0,obj.getString("time_tag").length()-1),DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			//System.out.println(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " xray flux " + obj.getDouble("flux"));
			flareData.put(dateTime,
					obj.getDouble("flux"));
		}
		try (FileWriter fw = new FileWriter("./out.csv")) {
			data.forEach((date, locations) -> {
				HashMap<Triplet, Double> angles = new HashMap<>();
				locations.add(createSun());

				for (Point2D obj1 : locations) {
					for (Point2D obj2 : locations) {
						for (Point2D obj3 : locations) {
							if (tripletValid(obj1, obj2, obj3)) {
								double angle = computeAngle(obj1, obj2, obj3);
								if ((angle >= 178) && (angle <= 182)) {
									angles.put(new Triplet(obj1, obj2, obj3), angle);
								}
							}
						}
					}
				}

				try {
					double sum = angles.entrySet().parallelStream()
							.map(e -> {
								int score = getScore(e.getKey().a.ident) + getScore(e.getKey().b.ident) + getScore(e.getKey().c.ident);
								double multiplier = (score * score / 1764.0) + 1;
								return score * multiplier;
							}).reduce(0d, Double::sum);
					double lncount = Math.log(angles.size());
					double mult = lncount * lncount + 1;

					LocalDateTime dat = dateLookup.get(date);
					String flare;
					flare = flareData.get(dat) + "";
					flare = flare == null?"":flare;

					fw.write(dat.format(DateTimeFormatter.ISO_DATE_TIME) + "," + sum * mult + ", " + flare + "\n");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
		System.out.println(System.currentTimeMillis() - start + " ms");
	}

	private int getScore(String ident) {
		return Integer.parseInt(ident.split("\\r?\\.")[1]);
	}

	private Point2D createSun() {
		return new Point2D("sol.24", 0,0);
	}

	private double computeAngle(Point2D A, Point2D B, Point2D C) {
		double a = A.distance(C);
		double b = B.distance(C);
		double c = A.distance(B);
		double left = Math.toDegrees(Math.acos(((a * a) + (b * b) - (c * c)) / (2d * a * b)));

		if(!Double.isNaN(left)) return left;
		else return -1;
	}

	private boolean tripletValid(Point2D obj1, Point2D obj2, Point2D obj3) {
		return !Objects.equals(obj1.ident, obj2.ident) && !Objects.equals(obj2.ident, obj3.ident) && !Objects.equals(obj1.ident, obj3.ident);
	}

	public static void main(String[] args) throws IOException {
		new OrbitalWizard("./process");
	}

	public record Point2D(String ident, double x, double y) {
		public Point2D add(Point2D b) {
			return new Point2D("nil",x+b.x, y+b.y);
		}

		public Point2D subtract(Point2D b) {
			return new Point2D("nil",x-b.x, y-b.y);
		}

		public Point2D mult(double t) {
			return new Point2D("nil", x*t,y*t);
		}

		public Point2D divide(double factor) {
			return new Point2D("nil", x/factor,y/factor);
		}

		public double distance(Point2D b) {
			return Math.sqrt(Math.pow(x-b.x,2) + Math.pow(y-b.y,2));
		}

		public double dot(Point2D b) {
			return x*b.x + y*b.y;
		}
	}

	/**
	 * Defines object triplet
	 * @param a start object
	 * @param b	outlier
	 * @param c end object
	 */
	private record Triplet(Point2D a, Point2D b, Point2D c) {
	}
}
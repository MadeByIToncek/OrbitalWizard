package space.itoncek.orbitalwizard;

import org.apache.commons.io.IOUtils;
import org.apfloat.Apfloat;
import org.apfloat.ApfloatMath;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class OrbitalWizard {
	public TreeMap<Double,ArrayList<Point2D>> data = new TreeMap<>();
	public TreeMap<LocalDateTime,Double> flareData = new TreeMap<>();
	public HashMap<Double, LocalDateTime> dateLookup = new HashMap<>();
	public OrbitalWizard() throws IOException {
		long start = System.currentTimeMillis();
		ArrayList<Request> requests = generateObjectRequests();

		String epochStart = "2024-01-01";
		String epochEnd = "2025-01-01";
		String step = "1 HOURS";

		if(!new File("./cache").exists()) new File("./cache").mkdirs();
		for (int i = 0; i < requests.size(); i++) {
			File f = new File("./cache/" + requests.get(i).id + ".vec");
			if(!f.exists()) {
				System.out.println("Downloading " + requests.get(i).id);
				String url = "https://ssd.jpl.nasa.gov/api/horizons.api?format=text&" +
						"OBJ_DATA=NO&" +
						"COMMAND=%s&".formatted(requests.get(i).id).replace(";","%3B") +
						"MAKE_EPHEM=YES&" +
						"EPHEM_TYPE=VECTORS&" +
						"CENTER='500%4010'&" +
						"START_TIME='%s'&".formatted(epochStart) +
						"STOP_TIME='%s'&".formatted(epochEnd) +
						"STEP_SIZE='%s'&".formatted(step.replace(" ", "%20")) +
						"VEC_TABLE=1&" +
						"REF_SYSTEM=ICRF&" +
						"REF_PLANE=ECLIPTIC&" +
						"VEC_CORR=NONE&" +
						"CAL_TYPE=M&" +
						"OUT_UNITS=KM-S&" +
						"VEC_LABELS=YES&" +
						"VEC_DELTA_T=NO&" +
						"CSV_FORMAT=YES";
				Optional<String> reduce = IOUtils.readLines(URI.create(url).toURL().openStream(), Charset.defaultCharset()).stream().reduce((a, b) -> a + "\n" + b);
				if(reduce.isPresent()){
					try (FileWriter fw = new FileWriter(f)){
						fw.write(reduce.get());
					}
				}
			}

			boolean reading = false;
			for (String line : Files.readAllLines(f.toPath(), Charset.defaultCharset())) {
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

						data.get(date).add(new Point2D(requests.get(i).id, requests.get(i).value, Double.parseDouble(elements[2].trim().strip()), Double.parseDouble(elements[3].trim().strip())));
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
								int score = e.getKey().a.value + e.getKey().b.value + e.getKey().c.value;
								double multiplier = (score * score / 1764.0) + 1;
								return score * multiplier;
							}).reduce(0d, Double::sum);
					double lncount = Math.log(angles.size());
					double mult = lncount * lncount + 1;


					LocalDateTime dat = dateLookup.get(date);
					String flare;
					flare = flareData.get(dat) + "";

					fw.write(dat.format(DateTimeFormatter.ISO_DATE_TIME) + "," + sum * mult + ", " + flare + "\n");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
		System.out.println(System.currentTimeMillis() - start + " ms");
	}

	private static ArrayList<Request> generateObjectRequests() {
		ArrayList<Request> requests = new ArrayList<>();
		requests.add(new Request("199",5));
		requests.add(new Request("299",5));
		requests.add(new Request("399",4));
		requests.add(new Request("499",4));
		requests.add(new Request("599",10));
		requests.add(new Request("699",8));
		requests.add(new Request("799",7));
		requests.add(new Request("899",6));
		requests.add(new Request("Pluto;",3));
		requests.add(new Request("Ceres;",2));
		requests.add(new Request("Juno;",1));
		requests.add(new Request("Vesta;",2));
		requests.add(new Request("Hygiea;",2));
		requests.add(new Request("Eunomia;",1));
		requests.add(new Request("Europa;",1));
		requests.add(new Request("Cybele;",1));
		requests.add(new Request("Sylvia;",1));
		requests.add(new Request("Camilla;",1));
		requests.add(new Request("Patientia;",1));
		requests.add(new Request("Davida;",1));
		requests.add(new Request("Hektor;",1));
		requests.add(new Request("Interamnia;",1));
		return requests;
	}

	private Point2D createSun() {
		return new Point2D("sol", 24, 0,0);
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
		new OrbitalWizard();
	}

	public record Point2D(String ident, int value, double x, double y) {
		public double distance(Point2D b) {
			return Math.sqrt(Math.pow(x-b.x,2) + Math.pow(y-b.y,2));
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
	private record Request(String id, int value){}
}
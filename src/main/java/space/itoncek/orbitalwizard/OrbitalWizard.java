package space.itoncek.orbitalwizard;

import com.sun.source.tree.Tree;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class OrbitalWizard {
	public TreeMap<Double,ArrayList<Point3D>> data = new TreeMap<>();
	public HashMap<Double,String> dateLookup = new HashMap<>();
	public OrbitalWizard(String path) throws IOException {
		long start = System.currentTimeMillis();
		for (File dir : Objects.requireNonNull(new File(path).listFiles())) {
			if(dir.isDirectory()) {
				for (File eph : Objects.requireNonNull(dir.listFiles())) {
					for (String line : Files.readAllLines(eph.toPath(), Charset.defaultCharset())) {
						//System.out.println(line);
						String[] elements = line.split("\\r?,");
						double date = Double.parseDouble(elements[0].trim().strip());

						if(!data.containsKey(date)) {
							data.put(date, new ArrayList<>(20));
						}

						if(!dateLookup.containsKey(date)) {
							dateLookup.put(date, elements[1].trim().strip());
						}

						data.get(date).add(new Point3D(eph.getName(), Double.parseDouble(elements[2].trim().strip()), Double.parseDouble(elements[3].trim().strip()), Double.parseDouble(elements[4].trim().strip())));
					}
				}
			}
		}
		try (FileWriter fw = new FileWriter("./out.csv")) {
			data.forEach((date, locations) -> {
				HashMap<ThreeSet, Double> qualityMap = new HashMap<>();

				for (Point3D obj1 : locations) {
					for (Point3D obj2 : locations) {
						ThreeSet ts = new ThreeSet(obj1.ident, createSun().ident, obj2.ident);
						if (tripletValid(obj1, createSun(), obj2) && !qualityMap.containsKey(ts)) {
							qualityMap.put(ts, computeDistance(obj1, createSun(), obj2));
						}
					}
				}

				ArrayList<Point3D> newLocations = new ArrayList<>(locations);
				newLocations.add(createSun());

				for (Point3D obj1 : newLocations) {
					for (Point3D obj2 : newLocations) {
						for (Point3D obj3 : newLocations) {
							ThreeSet ts = new ThreeSet(obj1.ident, obj2.ident, obj3.ident);
							if (tripletValid(obj1, obj2, obj3) && !qualityMap.containsKey(ts)) {
								qualityMap.put(ts, computeDistance(obj1, obj2, obj3));
							}
						}
					}
				}




				AtomicReference<Double> min = new AtomicReference<>(Double.MAX_VALUE);
				AtomicReference<ThreeSet> minset = new AtomicReference<>();

				qualityMap.forEach((set, val) -> {
					if (val < min.get()) {
						minset.set(set);
						min.set(val);
					}
				});

				try {
					fw.write(dateLookup.get(date) + "," + min.get() + "," + minset.get().toString() + "\n");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
		System.out.println(System.currentTimeMillis() - start + " ms");
	}

	private Point3D createSun() {
		return new Point3D("SUN", 0,0,0);
	}

	private double computeDistance(Point3D A, Point3D B, Point3D C) {
		Point3D d = C.subtract(B).divide(C.distance(B));
		Point3D v = A.subtract(B);
		double t = v.dot(d);
		Point3D P = B.add(d.mult(t));
		return P.distance(A);
	}

	private boolean tripletValid(Point3D obj1, Point3D obj2, Point3D obj3) {
		return !Objects.equals(obj1.ident, obj2.ident) && !Objects.equals(obj2.ident, obj3.ident) && !Objects.equals(obj1.ident, obj3.ident);
	}

	private boolean alreadyMatched(String obj1, String obj2, HashMap<String, String> matches) {
		return (matches.containsKey(obj1) && matches.get(obj1).equals(obj2)) || (matches.containsKey(obj2) && matches.get(obj2).equals(obj1));
	}

	public static void main(String[] args) throws IOException {
		if(args.length == 1) processFiles();
		else new OrbitalWizard("X:\\test");
	}

	private static void processFiles() throws IOException {
		for (File dir : Objects.requireNonNull(new File("./process/").listFiles())) {
			if(dir.isDirectory()) {
				File targetDir = new File("X:\\test\\" + dir.getName());
				for (File eph : Objects.requireNonNull(dir.listFiles())) {
					File targetFile = new File(targetDir + "\\" + eph.getName());
					boolean reading = false;
					try (FileWriter fw = new FileWriter(targetFile)) {
						for (String line : Files.readAllLines(eph.toPath(), Charset.defaultCharset())) {
							if (!reading) {
								if (line.contains("$$SOE")) {
									reading = true;
								}
							} else {
								if (line.contains("$$EOE")) break;
								else fw.write(line + "\n");
							}
						}
					}
				}
			}
		}
	}

	public record Point3D(String ident, double x, double y, double z) {
		public Point3D add(Point3D b) {
			return new Point3D("nil",x+b.x, y+b.y, z+b.z);
		}

		public Point3D subtract(Point3D b) {
			return new Point3D("nil",x-b.x, y-b.y, z-b.z);
		}

		public Point3D mult(double t) {
			return new Point3D("nil", x*t,y*t,z*t);
		}

		public Point3D divide(double factor) {
			return new Point3D("nil", x/factor,y/factor,z/factor);
		}

		public double distance(Point3D b) {
			return Math.sqrt(Math.pow(x-b.x,2) + Math.pow(y-b.y,2) + Math.pow(z-b.z,2));
		}

		public double dot(Point3D b) {
			return x*b.x + y*b.y + z*b.z;
		}
	}

	private record ThreeSet(String a, String b, String c){
		@Override
		public boolean equals(Object obj) {
			if(obj instanceof ThreeSet set) {
				HashSet<String> me = HashSet.newHashSet(3);
				me.add(a);
				me.add(b);
				me.add(c);
				return me.contains(set.a) && me.contains(set.b) && me.contains(set.c);
			} else return false;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(a) + Objects.hashCode(b) + Objects.hashCode(c);
		}

		@Override
		public String toString() {
			return a + " - " + b + " - " + c;
		}
	}
}
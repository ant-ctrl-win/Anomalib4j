package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

final class ScrewCalAccess {
    private final Set<Path> allowed;
    private final Map<String,Integer> reads=new TreeMap<>();
    ScrewCalAccess(Collection<Path> paths) {
        allowed=new HashSet<>();for(Path p:paths){Path absolute=p.toAbsolutePath().normalize();for(Path part:absolute)if(part.toString().equalsIgnoreCase("test"))throw new IllegalArgumentException("Forbidden data partition");allowed.add(absolute);}
    }
    Path require(Path path) {
        Path p=path.toAbsolutePath().normalize();if(!allowed.contains(p))throw new IllegalArgumentException("Outside CAL allowlist: "+p);
        reads.merge(p.toString(),1,Integer::sum);return p;
    }
    JsonNode json(Path path) throws Exception {return ComparisonArtifacts.JSON.readTree(require(path).toFile());}
    NpyMap.Map array(Path path) throws Exception {return NpyMap.read(require(path));}
    BufferedImage image(Path path) throws Exception {return ImageIO.read(require(path).toFile());}
    String hash(Path path) throws Exception {return NpyMap.sha256(require(path));}
    Map<String,Integer> reads(){return Collections.unmodifiableMap(reads);}
    static Path source(Path dataset,String name) {
        if(!name.matches("screw_good_[0-9]{3}\\.png")||name.equals("screw_good_179.png"))throw new IllegalArgumentException("Expected usable CAL good identity");
        return dataset.resolve("train/img").resolve(name).toAbsolutePath().normalize();
    }
    static void population(List<String> attempted,List<String> usable) {
        if(attempted.size()!=64||usable.size()!=63||new HashSet<>(attempted).size()!=64
                ||!attempted.contains("screw_good_179.png")||!attempted.stream().filter(n->!n.equals("screw_good_179.png")).toList().equals(usable))throw new IllegalArgumentException("Frozen CAL population/order mismatch");
        for(String name:usable)source(Path.of("dataset"),name);
    }
}

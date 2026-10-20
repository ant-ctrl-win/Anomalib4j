package io.github.antctrlwin.anomalib4j.evaluation;

import java.nio.file.*;
import java.util.*;
import java.util.function.ToDoubleFunction;
import static io.github.antctrlwin.anomalib4j.evaluation.GeneralityGeometry.*;
import static io.github.antctrlwin.anomalib4j.evaluation.GeneralityAudit.*;

public final class GeneralityDiagnostics {
    private GeneralityDiagnostics() { }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[1]);GeneralityInputs.verify(root);
        switch(args[0]) {
            case "train" -> {
                var rows=List.of(ComparisonArtifacts.JSON.readValue(root.resolve("poses-train.json").toFile(),PoseRow[].class));
                ComparisonArtifacts.json(root.resolve("summary-geometry.json"),poseGroups(rows,false));
                var changes=List.of(ComparisonArtifacts.JSON.readValue(root.resolve("perturbations.json").toFile(),PerturbRow[].class));
                ComparisonArtifacts.json(root.resolve("summary-perturbations.json"),perturbGroups(changes));
            }
            case "test" -> {
                var train=List.of(ComparisonArtifacts.JSON.readValue(root.resolve("poses-train.json").toFile(),PoseRow[].class));
                var test=List.of(ComparisonArtifacts.JSON.readValue(root.resolve("poses-test.json").toFile(),PoseRow[].class));
                ComparisonArtifacts.json(root.resolve("summary-test.json"),poseGroups(test,true));
                var outliers=new ArrayList<Map<String,Object>>();
                for(var row:test) {
                    var reference=train.stream().filter(r->r.category().equals(row.category())&&r.strategy()==row.strategy()&&r.role().equals("fit")
                            &&r.pose().state()!=State.SEGMENTATION_FAILURE).map(PoseRow::pose).toList();
                    var pose=row.pose();var result=new LinkedHashMap<String,Object>();
                    result.put("category",row.category());result.put("image",row.image());result.put("role",row.role());result.put("strategy",row.strategy());
                    result.put("state",pose.state());
                    result.put("centroid_outside_fit_box",outside(pose.cx(),reference,Pose::cx)||outside(pose.cy(),reference,Pose::cy));
                    result.put("area_outside_fit_range",outside(pose.area(),reference,Pose::area));
                    result.put("source_border",pose.border());result.put("canonical_clipped_fraction",pose.clippedFraction());
                    double period=switch(row.strategy()){case H4 -> 90;case AXIS -> 180;case AXIS_WITH_POLARITY -> 360;case CENTER_ONLY -> 0;};
                    if(period>0)result.put("nearest_fit_angle_distance",reference.stream().mapToDouble(p->GeneralityImages.angleError(pose.angle()-p.angle(),period)).min().orElseThrow());
                    outliers.add(result);
                }
                ComparisonArtifacts.json(root.resolve("test-outliers.json"),outliers);
            }
            default -> throw new IllegalArgumentException("Expected train/test");
        }
    }
    private static boolean outside(double value,List<Pose> reference,ToDoubleFunction<Pose> field) {
        return value<reference.stream().mapToDouble(field).min().orElseThrow()||value>reference.stream().mapToDouble(field).max().orElseThrow();
    }
    private static Map<String,Object> poseGroups(List<PoseRow> rows,boolean test) {
        var groups=new TreeMap<String,List<PoseRow>>();
        for(var row:rows) {
            String prefix=row.category()+"/"+row.strategy()+"/";
            groups.computeIfAbsent(prefix+row.role(),k->new ArrayList<>()).add(row);
            if(test)groups.computeIfAbsent(prefix+(row.role().equals("good")?"NORMAL":"ANOMALY"),k->new ArrayList<>()).add(row);
        }
        var result=new TreeMap<String,Object>();
        for(var entry:groups.entrySet()) {
            var all=entry.getValue().stream().map(PoseRow::pose).toList();
            var valid=all.stream().filter(p->p.state()!=State.SEGMENTATION_FAILURE).toList();
            var values=new LinkedHashMap<String,Object>();values.put("n",all.size());
            var states=new EnumMap<State,Long>(State.class);for(var s:State.values())states.put(s,all.stream().filter(p->p.state()==s).count());
            values.put("states",states);values.put("border",all.stream().filter(Pose::border).count());
            values.put("clipped",all.stream().filter(p->p.clippedFraction()>0).count());
            values.put("negative_mass_branch",valid.stream().filter(p->p.polarity()<0).count());
            if(!valid.isEmpty()) {
                values.put("cx",dist(valid,Pose::cx));values.put("cy",dist(valid,Pose::cy));values.put("angle",dist(valid,Pose::angle));
                values.put("area",dist(valid,Pose::area));values.put("anisotropy",dist(valid,Pose::anisotropy));
                values.put("abs_polarity",dist(valid,p->Math.abs(p.polarity())));values.put("h4",dist(valid,Pose::h4));
                values.put("clipped_fraction",dist(valid,Pose::clippedFraction));
            }
            result.put(entry.getKey(),values);
        }
        return result;
    }
    private static GeneralityCoherence.Distribution dist(List<Pose> poses,ToDoubleFunction<Pose> field) {
        return GeneralityCoherence.distribution(poses.stream().mapToDouble(field).toArray());
    }
    private static Map<String,Object> perturbGroups(List<PerturbRow> rows) {
        var groups=new TreeMap<String,List<PerturbRow>>();
        for(var row:rows) {
            String prefix=row.category()+"/"+row.strategy()+"/";
            var change=row.change();String level=change.angle()!=0?"ROTATION_"+change.angle():change.dxFraction()!=0?"X_"+change.dxFraction():"Y_"+change.dyFraction();
            groups.computeIfAbsent(prefix+level,k->new ArrayList<>()).add(row);
            groups.computeIfAbsent(prefix+"ALL",k->new ArrayList<>()).add(row);
        }
        var result=new TreeMap<String,Object>();
        for(var entry:groups.entrySet()) {
            var all=entry.getValue();var centroid=all.stream().filter(r->r.original().state()!=State.SEGMENTATION_FAILURE&&r.perturbed().state()!=State.SEGMENTATION_FAILURE).toList();
            var angular=all.stream().filter(r->r.angularError()!=null).toList();
            var values=new LinkedHashMap<String,Object>();values.put("n",all.size());
            values.put("trusted_pairs",all.stream().filter(r->r.original().state()==State.OK&&r.perturbed().state()==State.OK).count());
            values.put("perturbed_weak_or_failure",all.stream().filter(r->r.perturbed().state()!=State.OK).count());
            values.put("source_border",all.stream().filter(r->r.perturbed().border()).count());
            values.put("polarity_flips",all.stream().filter(PerturbRow::polarityFlip).count());
            values.put("angular_comparable_pairs",angular.size());
            if(!centroid.isEmpty())values.put("centroid_error",GeneralityCoherence.distribution(centroid.stream().mapToDouble(PerturbRow::centroidError).toArray()));
            if(!angular.isEmpty())values.put("angular_error",GeneralityCoherence.distribution(angular.stream().mapToDouble(PerturbRow::angularError).toArray()));
            result.put(entry.getKey(),values);
        }
        return result;
    }
}

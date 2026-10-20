package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.CanonicalRgbAcquisition;
import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.nio.file.*;
import java.util.*;

public final class ScrewCalNuisance {
    record Source(ScrewCalInputs.CalCase info,int[] logical,int[] referenceView) { }
    private ScrewCalNuisance() { }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[1]);switch(args[0]){
            case "prepare"->ScrewCalInputs.prepare(root);
            case "baseline"->score(root,true);
            case "run"->score(root,false);
            case "summarize"->ScrewCalSummary.run(root);
            case "verify"->{ScrewCalInputs.verify(root);System.out.println("CAL_PHOTOMETRIC - all CAL-only source/input/output guards verified");}
            default->throw new IllegalArgumentException("Expected prepare/baseline/run/summarize/verify");
        }
    }
    static Path condition(Path root,ScrewCalPhotometry.Family family,double level,String variant){return root.resolve("conditions").resolve(family.name()+"_"+String.format(Locale.ROOT,"%.2f",level)).resolve(variant);}
    private static void array(Path root,String name,double[] values,int columns) throws Exception {ComparisonArtifacts.writeMap(root.resolve(name+".npy"),values,63,columns,"CAL63", "screw-cal-frozen-pose-photometric-diagnostic",name);}
    private static void score(Path root,boolean baseline) throws Exception {
        String required=baseline?"prepare":"baseline";if(!Files.exists(root.resolve(required+"-evidence-hashes.json")))throw new IllegalStateException("Missing prior frozen phase: "+required);ScrewCalInputs.verify(root);
        var access=ScrewCalInputs.access(root);Path dataset=Path.of(ScrewCalInputs.read(root,"config.json").path("dataset").asText());var sources=new ArrayList<Source>();
        for(var c:ScrewCalInputs.cases(root)){var image=access.image(ScrewCalAccess.source(dataset,c.image()));try{if(image==null||image.getWidth()!=1024||image.getHeight()!=1024)throw new IllegalStateException("Expected native CAL1024");int[] logical=new int[1024*1024];CanonicalRgbAcquisition.read(image,logical);sources.add(new Source(c,logical,baseline?GeneralityImages.view(image):null));}finally{if(image!=null)image.flush();}}
        var records=new ArrayList<Map<String,Object>>();var bands=ScrewCalInputs.bands(root);int controls=0;
        try(var encoder=new OnnxMobileNetV4Encoder()) {
            for(var family:ScrewCalPhotometry.Family.values())for(double level:ScrewCalPhotometry.LEVELS)if((level==1)==baseline)for(String variant:List.of("identity","canonical")) {
                var model=ScrewCalInputs.model(variant,access);double[] mean=model.local().mean(),std=model.local().effectiveStd(),alpha=model.calibration().alpha(),beta=model.calibration().beta();
                Path out=condition(root,family,level,variant);Files.createDirectories(out.getParent());Files.createDirectory(out);
                NpyMap.Map retainedUnits=null,retainedRaw=null,retainedZ=null,retainedMax=null;
                if(baseline){Path old=ScrewCalInputs.base(variant);retainedUnits=access.array(old.resolve("calibration-units.npy"));retainedRaw=access.array(old.resolve("calibration-raw.npy"));retainedZ=access.array(old.resolve("calibration-native-z.npy"));retainedMax=access.array(old.resolve("calibration-image-scores.npy"));}
                else {Path zero=condition(root,family,1,variant);retainedUnits=NpyMap.read(zero.resolve("units.npy"));retainedRaw=NpyMap.read(zero.resolve("raw.npy"));retainedZ=NpyMap.read(zero.resolve("z.npy"));retainedMax=NpyMap.read(zero.resolve("max.npy"));}
                double[] units=new double[63*18816],rawMaps=new double[63*196],zMaps=rawMaps.clone(),maxima=new double[63];int index=0;
                for(var source:sources) {
                    var changed=ScrewCalPhotometry.perturb(source.logical,family,level);int[] view=ScrewCalPhotometry.view(changed.rgb());var pose=variant.equals("canonical")?source.info.pose():ScrewIdentityControl.IDENTITY;
                    float[] tensor=ScrewCalPhotometry.tensor(view,pose);String hash=ScrewCalPhotometry.hash(tensor);
                    if(baseline){if(!Arrays.equals(view,source.referenceView)||!Arrays.equals(tensor,ScrewCalPhotometry.tensor(source.referenceView,pose)))throw new IllegalStateException("STOP: factor1 tensor contract mismatch");if(variant.equals("identity")&&!hash.equals(source.info.identityTensorSha256()))throw new IllegalStateException("STOP: retained factor1 tensor mismatch");}
                    double[] unit=M2Model.normalize(encoder.extractPrepared(tensor)),raw=model.raw(unit),z=model.calibrate(raw);double max=M2Model.imageScore(z);
                    if(baseline&&(!Arrays.equals(unit,ScrewHeadSupport.row(retainedUnits,index))||!Arrays.equals(raw,ScrewHeadSupport.row(retainedRaw,index))||!Arrays.equals(z,ScrewHeadSupport.row(retainedZ,index))||max!=retainedMax.values()[index]))throw new IllegalStateException("STOP: factor1 retained CAL numerical mismatch");
                    System.arraycopy(unit,0,units,index*18816,18816);System.arraycopy(raw,0,rawMaps,index*196,196);System.arraycopy(z,0,zMaps,index*196,196);maxima[index]=max;
                    int p=ScrewIdentityControl.firstArgmax(z);double raw0=retainedRaw.values()[index*196+p],z0=retainedZ.values()[index*196+p],max0=retainedMax.values()[index];
                    double rawMax=Arrays.stream(raw).max().orElseThrow(),baselineRawMax=Arrays.stream(ScrewHeadSupport.row(retainedRaw,index)).max().orElseThrow();
                    var record=new LinkedHashMap<String,Object>();record.put("image",source.info.image());record.put("family",family.name());record.put("level",level);record.put("variant",variant);record.put("max_z",max);record.put("max_raw",rawMax);record.put("baseline_max_raw",baselineRawMax);record.put("delta_max_raw",rawMax-baselineRawMax);record.put("delta_max_z",max-max0);record.put("baseline_max_z",max0);record.put("max_cell",p);
                    record.put("origin",ScrewHeadSupport.region(bands,variant.equals("identity")?source.info.pose():ScrewIdentityControl.IDENTITY,p));record.put("raw_at_winner",raw[p]);record.put("alpha_at_winner",alpha[p]);record.put("beta_at_winner",beta[p]);record.put("baseline_raw_at_winner",raw0);record.put("raw_growth_at_winner",raw[p]-raw0);record.put("same_cell_z_growth",(raw[p]-raw0)/beta[p]);record.put("winner_switch_penalty",z0-max0);record.put("tensor_sha256",hash);record.put("clipped_channels",changed.clippedChannels());record.put("frozen_pose",pose);
                    for(int threshold:new int[]{3,5,10})record.put("cells_above_"+threshold,Arrays.stream(z).filter(v->v>threshold).count());
                    double[] contributions=new double[96];for(int c=0;c<96;c++){int at=p*96+c;double d=(unit[at]-mean[at])/std[at],d0=(retainedUnits.values()[index*18816+at]-mean[at])/std[at];contributions[c]=(d*d-d0*d0)/96;}
                    record.put("channel_growth",contributions);double positive=0,squares=0;int positiveCount=0;for(double value:contributions)if(value>0){positive+=value;squares+=value*value;positiveCount++;}double[] sorted=contributions.clone();Arrays.sort(sorted);double top=0;for(int c=91;c<96;c++)top+=Math.max(0,sorted[c]);record.put("positive_channel_count",positiveCount);record.put("top5_positive_growth_share",positive==0?0:top/positive);record.put("positive_effective_participation",squares==0?0:positive*positive/squares);
                    if(Math.abs(max-max0-(raw[p]-raw0)/beta[p]-(z0-max0))>1e-10||Math.abs(Arrays.stream(contributions).sum()-(raw[p]-raw0))>1e-10)throw new IllegalStateException("Winner/channel decomposition mismatch");records.add(record);index++;
                }
                array(out,"units",units,18816);array(out,"raw",rawMaps,196);array(out,"z",zMaps,196);array(out,"max",maxima,1);controls+=63;
                System.out.println("CAL_PHOTOMETRIC - "+(baseline?"BASELINE":"PERTURB")+" - "+family+" "+level+" "+variant+" - CAL63 complete; cumulative "+controls);
            }
        }
        ScrewCalInputs.json(root,baseline?"baseline-records.json":"perturbed-records.json",records);ScrewCalInputs.json(root,baseline?"baseline-access-audit.json":"scoring-access-audit.json",access.reads());
        if(baseline)ScrewCalInputs.json(root,"baseline-parity.json",Map.of("status","EXACT","evaluations",controls,"normalized_descriptor_max_error",0,"raw_max_error",0,"z_max_error",0,"native_max_error",0,"identity_retained_tensor_hashes",189,"canonical_executable_tensor_checks",189,"prior_canonical_tensor_hashes_available",false));
        ScrewCalInputs.mark(root,baseline?"baseline":"scoring");
    }
}

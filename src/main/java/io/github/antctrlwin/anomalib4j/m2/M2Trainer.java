package io.github.antctrlwin.anomalib4j.m2;

import java.util.Arrays;
import java.util.List;

/** Fit and held-out calibration are explicit, separate operations. */
public final class M2Trainer {
    private M2Trainer() { }
    public static M2Moments fitStatistics(List<double[]> fitUnits) {
        var statistics = new M2Moments(M2Model.ELEMENTS);
        for (double[] unit : fitUnits) { M2Model.requireUnit(unit); statistics.add(unit); }
        statistics.mean();
        return statistics;
    }
    public static M2Model uncalibrated(String hash, M2Moments fit) {
        double[] scales = new double[M2Model.CELLS];
        Arrays.fill(scales, 1);
        return new M2Model(hash, new M2Model.Local(fit.mean(), fit.effectiveStd()),
                new M2Model.Calibration(new double[M2Model.CELLS], scales));
    }
    public static M2Moments calibrationStatistics(M2Model fitted, List<double[]> heldOutUnits) {
        var calibration = new M2Moments(M2Model.CELLS);
        for (double[] unit : heldOutUnits) calibration.add(fitted.raw(unit));
        calibration.mean();
        return calibration;
    }
    public static M2Model calibrated(M2Model fitted, M2Moments calibration) {
        return new M2Model(fitted.encoderSha256(), fitted.local(),
                new M2Model.Calibration(calibration.mean(), calibration.effectiveStd()));
    }
}

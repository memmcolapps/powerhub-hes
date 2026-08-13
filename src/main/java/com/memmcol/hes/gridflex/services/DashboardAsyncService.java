package com.memmcol.hes.gridflex.services;

import com.memmcol.hes.gridflex.records.DashboardSummaryResponse;
import com.memmcol.hes.model.MetersConnectionEvent;
import com.memmcol.hes.netty.NettyServerHolder;
import com.memmcol.hes.repository.*;
import com.memmcol.hes.repository.MeterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardAsyncService {
    private final NettyServerHolder holder;
    private final MetersConnectionEventRepository connectionEventRepository;
    private final SchedulerRepository schedulerRepository;
    private final EventLogRepository eventLogRepository;
    private final MeterRepository meterRepository;
    private final MetersConnectionEventRepository metersConnectionEventRepository;


    @Value("${dashboard.topN:5}")
    private int topN;

    // ======================
    // 1️⃣ Meter summary
    // ======================
    @Async
    @Cacheable(cacheNames = "dashboardMeterSummary", key = "'summary'")
    public CompletableFuture<DashboardSummaryResponse.MeterSummary> getMeterSummaryAsync() {
        log.info("🔄 Fetching meter summary from DB …");
        try {
            int total = safeInt(meterRepository.count());
            int online = safeInt(holder.getActiveMeterCount());
            int offline = Math.max(total - online, 0);
            int failedCommands = 0;

            DashboardSummaryResponse.MeterSummary summary =
                    new DashboardSummaryResponse.MeterSummary(total, online, offline, failedCommands);

            return CompletableFuture.completedFuture(summary);
        } catch (Exception e) {
            log.error("❌ Error fetching meter summary: {}", e.getMessage());
            return CompletableFuture.completedFuture(new DashboardSummaryResponse.MeterSummary(0, 0, 0, 0));
        }
    }

    // ======================
    // 2️⃣ Communication logs
    // ======================
    @Async
    @Cacheable(cacheNames = "dashboardCommunicationLogs", key = "'logs'")
    public CompletableFuture<List<DashboardSummaryResponse.CommunicationLogPoint>> getCommunicationLogsAsync() {
        log.info("🔄 Fetching communication logs from DB …");
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime fromTime = now.minusHours(24);

            List<MetersConnectionEvent> recentEvents = connectionEventRepository.findRecentEvents(fromTime);

            // Define 6 intervals (4 hours each)
            List<DashboardSummaryResponse.CommunicationLogPoint> logs = new ArrayList<>();
            for (int i = 4; i <= 24; i += 4) {
                LocalDateTime start = now.minusHours(i);
                LocalDateTime end = now.minusHours(i - 4);

                long count = recentEvents.stream()
                        .filter(e -> e.getOnlineTime().isAfter(start) && e.getOnlineTime().isBefore(end))
                        .count();

                logs.add(new DashboardSummaryResponse.CommunicationLogPoint(i + " hrs", (int) count));
            }

            return CompletableFuture.completedFuture(logs);
        } catch (Exception e) {
            log.error("❌ Error fetching communication logs: {}", e.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    // ======================
    // 3️⃣ Scheduler Rate
    // ======================
    @Async
    @Cacheable(cacheNames = "dashboardSchedulerRate", key = "'rate'")
    public CompletableFuture<DashboardSummaryResponse.DataSchedulerRate> getSchedulerRateAsync() {
        log.info("🔄 Fetching scheduler rate from DB …");
        try {
            long active = schedulerRepository.countByJobStatusIgnoreCase("COMPLETED") + schedulerRepository.countByJobStatusIgnoreCase("RUNNING");
            long paused = schedulerRepository.countByJobStatusIgnoreCase("PAUSED");

            double total = active + paused;
            double activePercent = 0.0;
            double pausedPercent = 0.0;

            if (total > 0) {
                activePercent = (active / total) * 100.0;
                pausedPercent = (paused / total) * 100.0;
            }
            return CompletableFuture.completedFuture(
                    new DashboardSummaryResponse.DataSchedulerRate(activePercent, pausedPercent)
            );
        } catch (Exception e) {
            log.error("❌ Error fetching scheduler rate: {}", e.getMessage());
            return CompletableFuture.completedFuture(new DashboardSummaryResponse.DataSchedulerRate(0, 0));
        }
    }

    // ======================
    // 4️⃣ Communication Report
    // ======================
    @Async
    @Cacheable(cacheNames = "dashboardCommunicationReport", key = "'report'")
    public CompletableFuture<List<DashboardSummaryResponse.CommunicationReportRow>> getCommunicationReportAsync(
            int page, int size, String sortBy, boolean ascending
    ) {
        log.info("🔄 Fetching communication report from DB …");
        try {
            List<DashboardSummaryResponse.CommunicationReportRow> communicationReport = new ArrayList<>();

            // 1️⃣ Meter model map
            List<Object[]> meterModels = meterRepository.findAllMeterModels();
            Map<String, String> meterModelMap = meterModels.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r[0],
                            r -> (String) r[1],
                            (existing, replacement) -> existing // keep first if duplicates
                    ));

            // 2️⃣ Latest connection events
            List<Object[]> connectionEvents = metersConnectionEventRepository.findLatestConnectionEvents();

            Set<String> duplicates = connectionEvents.stream()
                    .collect(Collectors.groupingBy(r -> (String) r[0], Collectors.counting()))
                    .entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            if (!duplicates.isEmpty()) {
                log.warn("⚠️ Duplicate meterNos found in connection events: {}", duplicates);
            }

            Map<String, Object[]> connectionMap = connectionEvents.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r[0],
                            r -> r,
                            (existing, replacement) -> replacement // keep latest if duplicates
                    ));

            // 3️⃣ Tamper events
            List<Object[]> tamperEvents = eventLogRepository.findLatestEventLogsByType(3);
            Map<String, Object[]> tamperMap = tamperEvents.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r[0],
                            r -> r,
                            (existing, replacement) -> replacement
                    ));

            // 4️⃣ Relay events
            List<Object[]> relayEvents = eventLogRepository.findLatestEventLogsByType(4);
            Map<String, Object[]> relayMap = relayEvents.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r[0],
                            r -> r,
                            (existing, replacement) -> replacement
                    ));

            // 5️⃣ Build communication report
            int sn = 1;
            for (String meterNo : meterModelMap.keySet()) {
                Object[] conn = connectionMap.get(meterNo);
                Object[] tamp = tamperMap.get(meterNo);
                Object[] relay = relayMap.get(meterNo);

                communicationReport.add(
                        new DashboardSummaryResponse.CommunicationReportRow(
                                String.format("%02d", sn++),
                                meterNo,
                                meterModelMap.get(meterNo),
                                conn != null ? (String) conn[1] : "Unknown",
                                conn != null ? conn[2].toString() : "N/A",
                                tamp != null ? (String) tamp[1] : "No Tamper",
                                tamp != null ? tamp[2].toString() : "N/A",
                                relay != null ? (String) relay[1] : "Disconnected",
                                relay != null ? relay[2].toString() : "N/A"
                        )
                );
            }

            // ✅ Limit result to top 5 rows (for dashboard summary)
            List<DashboardSummaryResponse.CommunicationReportRow> limitedReport = communicationReport
                    .stream()
                    .limit(topN)
                    .collect(Collectors.toList());

            log.info("📊 Returning top {} communication report rows for dashboard.", limitedReport.size());

            // 🔢 Optional Sorting
            Comparator<DashboardSummaryResponse.CommunicationReportRow> comparator = switch (sortBy) {
                case "meterNo" -> Comparator.comparing(DashboardSummaryResponse.CommunicationReportRow::meterNo);
                case "status" -> Comparator.comparing(DashboardSummaryResponse.CommunicationReportRow::status);
                default -> Comparator.comparing(DashboardSummaryResponse.CommunicationReportRow::lastSync);
            };

            if (!ascending) {
                comparator = comparator.reversed();
            }

            // Apply sorting
            List<DashboardSummaryResponse.CommunicationReportRow> sortedReport =
                    communicationReport.stream()
                            .sorted(comparator)
                            .toList();

// 📄 Pagination
            int fromIndex = Math.max(0, page * size);
            int toIndex = Math.min(fromIndex + size, sortedReport.size());

            List<DashboardSummaryResponse.CommunicationReportRow> paginatedReport =
                    fromIndex < sortedReport.size()
                            ? sortedReport.subList(fromIndex, toIndex)
                            : List.of();

            return CompletableFuture.completedFuture(paginatedReport);
        } catch (Exception e) {
            log.error("❌ Error fetching communication report: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    public CompletableFuture<List<DashboardSummaryResponse.CommunicationReportRow>> getCommunicationReportAsync() {
        log.info("🔄 Fetching communication report from DB …");
        try {
            List<DashboardSummaryResponse.CommunicationReportRow> communicationReport = new ArrayList<>();

            // 1️⃣ Meter model map
            List<Object[]> meterModels = meterRepository.findAllMeterModels();
            Map<String, String> meterModelMap = meterModels.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r[0],
                            r -> (String) r[1],
                            (existing, replacement) -> existing // keep first if duplicates
                    ));

            // 2️⃣ Latest connection events
            List<Object[]> connectionEvents = metersConnectionEventRepository.findLatestConnectionEvents();

            Set<String> duplicates = connectionEvents.stream()
                    .collect(Collectors.groupingBy(r -> (String) r[0], Collectors.counting()))
                    .entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            if (!duplicates.isEmpty()) {
                log.warn("⚠️ Duplicate meterNos found in connection events: {}", duplicates);
            }

            Map<String, Object[]> connectionMap = connectionEvents.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r[0],
                            r -> r,
                            (existing, replacement) -> replacement // keep latest if duplicates
                    ));

            // 3️⃣ Tamper events
            List<Object[]> tamperEvents = eventLogRepository.findLatestEventLogsByType(3);
            Map<String, Object[]> tamperMap = tamperEvents.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r[0],
                            r -> r,
                            (existing, replacement) -> replacement
                    ));

            // 4️⃣ Relay events
            List<Object[]> relayEvents = eventLogRepository.findLatestEventLogsByType(4);
            Map<String, Object[]> relayMap = relayEvents.stream()
                    .collect(Collectors.toMap(
                            r -> (String) r[0],
                            r -> r,
                            (existing, replacement) -> replacement
                    ));

            // 5️⃣ Build communication report
            int sn = 1;
            for (String meterNo : meterModelMap.keySet()) {
                Object[] conn = connectionMap.get(meterNo);
                Object[] tamp = tamperMap.get(meterNo);
                Object[] relay = relayMap.get(meterNo);

                communicationReport.add(
                        new DashboardSummaryResponse.CommunicationReportRow(
                                String.format("%02d", sn++),
                                meterNo,
                                meterModelMap.get(meterNo),
                                conn != null ? (String) conn[1] : "Unknown",
                                conn != null ? conn[2].toString() : "N/A",
                                tamp != null ? (String) tamp[1] : "No Tamper",
                                tamp != null ? tamp[2].toString() : "N/A",
                                relay != null ? (String) relay[1] : "Disconnected",
                                relay != null ? relay[2].toString() : "N/A"
                        )
                );
            }

            // ✅ Limit result to top 5 rows (for dashboard summary)
            List<DashboardSummaryResponse.CommunicationReportRow> limitedReport = communicationReport
                    .stream()
                    .limit(topN)
                    .collect(Collectors.toList());

            log.info("📊 Returning top {} communication report rows for dashboard.", limitedReport.size());

            return CompletableFuture.completedFuture(limitedReport);
        } catch (Exception e) {
            log.error("❌ Error fetching communication report: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(List.of());
        }
    }


    // Utility
    private int safeInt(Number value) {
        return value == null ? 0 : value.intValue();
    }
}

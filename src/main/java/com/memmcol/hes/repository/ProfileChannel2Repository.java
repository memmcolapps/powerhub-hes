package com.memmcol.hes.repository;

import com.memmcol.hes.model.ProfileChannel2Reading;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ProfileChannel2Repository {

    public <S extends ProfileChannel2Reading> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        if (entities != null) {
            entities.forEach(result::add);
        }
        return result;
    }

    public Optional<ProfileChannel2Reading> findTopByMeterSerialOrderByEntryIndexDesc(String meterSerial) {
        return Optional.empty();
    }

    public Optional<ProfileChannel2Reading> findTopByMeterSerialOrderByEntryTimestampDesc(String meterSerial) {
        return Optional.empty();
    }

    public boolean existsByMeterSerialAndEntryIndex(String meterSerial, Long entryIndex) {
        return false;
    }

    public List<Long> findExistingIndexesWithTimestamps(
            String serial,
            List<Integer> entryIndexes,
            List<LocalDateTime> timestamps
    ) {
        return new ArrayList<>();
    }

    public List<LocalDateTime> findExistingTimestamps(String serial, List<LocalDateTime> timestamps) {
        return new ArrayList<>();
    }

    public LocalDateTime findLatestTimestamp(String serial) {
        return null;
    }
}

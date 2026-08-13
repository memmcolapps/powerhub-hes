# Quartz Scheduler Relations (qrtz_*) Analysis

The HES application integrates with the Quartz Scheduler to manage backgrounds jobs (such as profile synchronization). To run the scheduler successfully, the following `qrtz_*` relations must exist in the shared SaaS database.

## 1. List of Required Quartz Tables

The following 11 tables are required by the Quartz JDBC JobStore:

1. **`qrtz_job_details`**: Stores detail about defined jobs.
   - *Primary Key*: `(sched_name, job_name, job_group)`
2. **`qrtz_triggers`**: Stores general properties of triggers.
   - *Primary Key*: `(sched_name, trigger_name, trigger_group)`
   - *Foreign Key*: `fk_qrtz_triggers_qrtz_job_details` referencing `qrtz_job_details`
3. **`qrtz_simple_triggers`**: Stores simple trigger details (e.g. repeat count/interval).
   - *Primary Key*: `(sched_name, trigger_name, trigger_group)`
   - *Foreign Key*: `fk_qrtz_simple_triggers_qrtz_triggers` referencing `qrtz_triggers`
4. **`qrtz_cron_triggers`**: Stores cron trigger details (e.g. cron expression).
   - *Primary Key*: `(sched_name, trigger_name, trigger_group)`
   - *Foreign Key*: `fk_qrtz_cron_triggers_qrtz_triggers` referencing `qrtz_triggers`
5. **`qrtz_simprop_triggers`**: Stores properties of simulation triggers.
   - *Primary Key*: `(sched_name, trigger_name, trigger_group)`
   - *Foreign Key*: `fk_qrtz_simprop_triggers_qrtz_triggers` referencing `qrtz_triggers`
6. **`qrtz_blob_triggers`**: Stores BLOB trigger details.
   - *Primary Key*: `(sched_name, trigger_name, trigger_group)`
   - *Foreign Key*: `fk_qrtz_blob_triggers_qrtz_triggers` referencing `qrtz_triggers`
7. **`qrtz_calendars`**: Stores calendar information.
   - *Primary Key*: `(sched_name, calendar_name)`
8. **`qrtz_paused_trigger_grps`**: Tracks paused trigger groups.
   - *Primary Key*: `(sched_name, trigger_group)`
9. **`qrtz_fired_triggers`**: Tracks currently fired (executing) triggers.
   - *Primary Key*: `(sched_name, entry_id)`
10. **`qrtz_scheduler_state`**: Tracks state and heartbeat of scheduler instances.
    - *Primary Key*: `(sched_name, instance_name)`
11. **`qrtz_locks`**: Handles database locking for transaction safety.
    - *Primary Key*: `(sched_name, lock_name)`

---

## 2. Foreign Key Constraints

To maintain referential integrity, the following 5 constraints must be established:

- **`fk_qrtz_blob_triggers_qrtz_triggers`**:
  `ALTER TABLE qrtz_blob_triggers ADD CONSTRAINT fk_qrtz_blob_triggers_qrtz_triggers FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group) ON DELETE CASCADE;`
- **`fk_qrtz_cron_triggers_qrtz_triggers`**:
  `ALTER TABLE qrtz_cron_triggers ADD CONSTRAINT fk_qrtz_cron_triggers_qrtz_triggers FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group) ON DELETE CASCADE;`
- **`fk_qrtz_simple_triggers_qrtz_triggers`**:
  `ALTER TABLE qrtz_simple_triggers ADD CONSTRAINT fk_qrtz_simple_triggers_qrtz_triggers FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group) ON DELETE CASCADE;`
- **`fk_qrtz_simprop_triggers_qrtz_triggers`**:
  `ALTER TABLE qrtz_simprop_triggers ADD CONSTRAINT fk_qrtz_simprop_triggers_qrtz_triggers FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group) ON DELETE CASCADE;`
- **`fk_qrtz_triggers_qrtz_job_details`**:
  `ALTER TABLE qrtz_triggers ADD CONSTRAINT fk_qrtz_triggers_qrtz_job_details FOREIGN KEY (sched_name, job_name, job_group) REFERENCES qrtz_job_details (sched_name, job_name, job_group) ON DELETE NO ACTION;`

# Portal UI v0.33

## Changes

- Removed the Settings navigation section and the global Refresh button.
- The main navigation now contains only Portals and Messages.
- Extended the admin portal setup wizard:
  - selecting “Connect smart process” changes the final technical step button from Finish to Next;
  - the final wizard step loads and allows explicit selection of the CRM category, open stage, completed stage, and responsible employee;
  - the CRM configuration is saved only after pressing Finish on that final step.
- Reworked the admin portal settings layout:
  - primary portal parameters and technical setup remain in the first row;
  - support employees and CRM settings are placed side by side in the second row with the same column proportions;
  - the employee list has its own compact scroll area.
- Bumped static asset cache versions to `v=033`.

## Database and backend

No database migration is required. Existing REST endpoints and backend routing/CRM synchronization are unchanged.

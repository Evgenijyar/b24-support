# Portal and conversation UI v0.3.1

## Changes

- The portal type selector now uses visible native radio buttons.
- Portal selection remains active across the Portals and Messages pages.
- Messages are grouped by support ticket:
  - conversations are listed on the left;
  - the complete chronological thread is shown on the right;
  - requester and support agent names are preserved;
  - selecting the admin portal shows conversations from all client portals;
  - selecting a client portal shows only that client's conversations.
- Portal deletion is available:
  - from the small cross in the left portal list;
  - from the Delete button in the portal header;
  - through a styled confirmation dialog.
- Deleting a portal first attempts to unregister its Bitrix24 bot, then removes the local portal data.
- Database migration V9 changes portal-related foreign keys to ON DELETE CASCADE so local tickets, messages, routes and events are deleted with the portal.

## New API

- `GET /api/support/conversations?portalId={portalId}`
- `GET /api/support/conversations/{ticketId}/messages`

## Deployment

Flyway applies `V9__portal_delete_cascade.sql` automatically during deployment.

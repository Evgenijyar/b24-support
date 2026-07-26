# Portal UI v030

## Changes

- Removed the Overview and separate Admin Portal pages.
- Portals are now managed from one workspace: a scrollable portal list on the left and selected portal settings on the right.
- Admin portal is always sorted before client portals.
- Removed the duplicate Add Portal button from the workspace header.
- Portal header now shows organization name, Bitrix24 domain and portal role.
- Added a multi-step portal setup wizard.
- Portal type is selected with radio buttons.
- Client code, domain, member_id and manual status are hidden from the creation form.
- Domain is extracted server-side from the webhook URL.
- Client phone is mandatory for client portals.
- Admin setup wizard checks the webhook, loads users, saves support staff, registers the bot, repairs routing and optionally connects a smart process.
- Client setup wizard checks the webhook, registers the client bot and repairs routing.
- Existing portal settings, employee selection, bot operations and CRM setup remain available in the portal workspace.

## Smart process auto-setup in the add wizard

When a process is selected during initial admin setup, the wizard:

1. selects the default category (or the first available category),
2. prefers a PROCESS stage named `В работе`,
3. prefers a SUCCESS stage containing `Заверш`,
4. uses the first selected support employee as responsible,
5. saves the CRM mapping through the existing CRM configuration API.

The full CRM wizard remains available later for manual correction.

# Front end

The user interface is built in Java with Vaadin Flow, inside
`backend/src/main/java/com/example/leaveplatform/ui/` — see ADR-014. No separate React
client is used, so this directory stays empty.

Vaadin generates its browser bundle at build time under
`backend/src/main/frontend/generated/` and `backend/src/main/bundles/`; both are
ignored by Git and must never be edited by hand.

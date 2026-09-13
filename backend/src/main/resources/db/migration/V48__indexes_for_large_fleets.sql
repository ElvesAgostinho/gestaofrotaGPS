-- Fatia 42: indices para frotas grandes (500 viaturas, 10 000 ordens).
--
-- Cada um cobre uma listagem ou um filtro que ate aqui varria a tabela toda.
CREATE INDEX ix_work_orders_org_opened ON work_orders (organization_id, opened_at DESC);
CREATE INDEX ix_work_orders_assigned ON work_orders (assigned_to_user_id, status);
CREATE INDEX ix_work_orders_org_completed ON work_orders (organization_id, completed_at);
CREATE INDEX ix_assets_org_archived ON assets (organization_id, is_archived, status);
CREATE INDEX ix_assets_org_tag ON assets (organization_id, tag);
CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_failures_org_detected ON failures (organization_id, detected_at);
CREATE INDEX ix_checklist_exec_org ON checklist_executions (organization_id, performed_at);
CREATE INDEX ix_meter_readings_meter_created ON meter_readings (asset_meter_id, created_at);
CREATE INDEX ix_fuel_records_org_asset ON fuel_records (organization_id, asset_id, filled_at);
CREATE INDEX ix_tyres_asset_status ON tyres (asset_id, status);
CREATE INDEX ix_driver_assignments_open ON driver_assignments (driver_id, ended_at);

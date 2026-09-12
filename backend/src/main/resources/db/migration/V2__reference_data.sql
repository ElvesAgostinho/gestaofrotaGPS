-- Dados de referência sempre presentes (planos, marcas, configuração base).
-- Os dados de demonstração (utilizador demo, viaturas) são carregados à parte
-- pelo DemoDataSeeder, apenas quando autocare.seed.demo=true.

-- Configuração da aplicação
INSERT INTO app_config (id, config_key, config_value, updated_at) VALUES
 ('cfg-app-name',    'app.name',         'AutoCare',                      CURRENT_TIMESTAMP),
 ('cfg-app-tagline', 'app.tagline',      'A inteligência da sua viatura', CURRENT_TIMESTAMP),
 ('cfg-app-currency','app.currency',     'AOA',                           CURRENT_TIMESTAMP),
 ('cfg-app-locale',  'app.locale',       'pt-AO',                         CURRENT_TIMESTAMP),
 ('cfg-app-email',   'app.supportEmail', 'apoio@autocare.ao',             CURRENT_TIMESTAMP);

-- Planos de assinatura
INSERT INTO plans (id, code, name, max_vehicles, has_gps, price_monthly, currency, features_json, is_active, created_at, updated_at) VALUES
 ('plan-free', 'FREE', 'Grátis', 1, FALSE, 0, 'AOA',
  '["1 viatura","Manutenção e documentos","Alertas de caducidade"]', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('plan-personal', 'PERSONAL', 'Pessoal', 5, FALSE, 2500, 'AOA',
  '["Até 5 viaturas","Manutenção, documentos e despesas","Abastecimentos e relatórios","Comparação entre viaturas"]', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('plan-gps', 'GPS', 'GPS', 5, TRUE, 6500, 'AOA',
  '["Tudo do plano Pessoal","Rastreamento GPS em tempo real","Histórico de percursos e geofencing","Alertas GPS"]', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('plan-fleet', 'FLEET', 'Frota', 50, TRUE, 25000, 'AOA',
  '["Até 50 viaturas","Gestão de condutores e oficinas","Relatórios avançados","Vários utilizadores por empresa"]', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Provedor GPS de demonstração
INSERT INTO gps_providers (id, name, adapter, is_active, created_at, updated_at) VALUES
 ('gps-demo', 'Modo demonstração', 'DEMO', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Marcas comuns em Angola (a lista não limita o cadastro)
INSERT INTO vehicle_brands (id, name) VALUES
 ('brand-toyota','Toyota'),
 ('brand-hyundai','Hyundai'),
 ('brand-kia','Kia'),
 ('brand-mercedes','Mercedes-Benz'),
 ('brand-bmw','BMW'),
 ('brand-nissan','Nissan'),
 ('brand-mitsubishi','Mitsubishi'),
 ('brand-ford','Ford'),
 ('brand-chevrolet','Chevrolet'),
 ('brand-vw','Volkswagen'),
 ('brand-lexus','Lexus'),
 ('brand-landrover','Land Rover'),
 ('brand-peugeot','Peugeot'),
 ('brand-suzuki','Suzuki'),
 ('brand-honda','Honda'),
 ('brand-isuzu','Isuzu'),
 ('brand-mazda','Mazda');

INSERT INTO vehicle_models (id, brand_id, name) VALUES
 ('m-toyota-hilux','brand-toyota','Hilux'),
 ('m-toyota-corolla','brand-toyota','Corolla'),
 ('m-toyota-rav4','brand-toyota','RAV4'),
 ('m-toyota-landcruiser','brand-toyota','Land Cruiser'),
 ('m-toyota-hiace','brand-toyota','Hiace'),
 ('m-toyota-yaris','brand-toyota','Yaris'),
 ('m-hyundai-tucson','brand-hyundai','Tucson'),
 ('m-hyundai-santafe','brand-hyundai','Santa Fe'),
 ('m-hyundai-accent','brand-hyundai','Accent'),
 ('m-hyundai-creta','brand-hyundai','Creta'),
 ('m-hyundai-h1','brand-hyundai','H1'),
 ('m-kia-sportage','brand-kia','Sportage'),
 ('m-kia-sorento','brand-kia','Sorento'),
 ('m-kia-rio','brand-kia','Rio'),
 ('m-kia-picanto','brand-kia','Picanto'),
 ('m-mercedes-c','brand-mercedes','Classe C'),
 ('m-mercedes-e','brand-mercedes','Classe E'),
 ('m-mercedes-glc','brand-mercedes','GLC'),
 ('m-mercedes-sprinter','brand-mercedes','Sprinter'),
 ('m-bmw-3','brand-bmw','Série 3'),
 ('m-bmw-5','brand-bmw','Série 5'),
 ('m-bmw-x3','brand-bmw','X3'),
 ('m-bmw-x5','brand-bmw','X5'),
 ('m-nissan-navara','brand-nissan','Navara'),
 ('m-nissan-xtrail','brand-nissan','X-Trail'),
 ('m-nissan-patrol','brand-nissan','Patrol'),
 ('m-mitsubishi-l200','brand-mitsubishi','L200'),
 ('m-mitsubishi-pajero','brand-mitsubishi','Pajero'),
 ('m-mitsubishi-outlander','brand-mitsubishi','Outlander'),
 ('m-ford-ranger','brand-ford','Ranger'),
 ('m-ford-everest','brand-ford','Everest'),
 ('m-ford-focus','brand-ford','Focus'),
 ('m-chevrolet-s10','brand-chevrolet','S10'),
 ('m-chevrolet-trailblazer','brand-chevrolet','Trailblazer'),
 ('m-vw-amarok','brand-vw','Amarok'),
 ('m-vw-golf','brand-vw','Golf'),
 ('m-vw-polo','brand-vw','Polo'),
 ('m-vw-tiguan','brand-vw','Tiguan'),
 ('m-lexus-rx','brand-lexus','RX'),
 ('m-lexus-lx','brand-lexus','LX'),
 ('m-landrover-defender','brand-landrover','Defender'),
 ('m-landrover-discovery','brand-landrover','Discovery'),
 ('m-landrover-rangerover','brand-landrover','Range Rover'),
 ('m-peugeot-3008','brand-peugeot','3008'),
 ('m-peugeot-208','brand-peugeot','208'),
 ('m-peugeot-partner','brand-peugeot','Partner'),
 ('m-suzuki-vitara','brand-suzuki','Vitara'),
 ('m-suzuki-jimny','brand-suzuki','Jimny'),
 ('m-suzuki-swift','brand-suzuki','Swift'),
 ('m-honda-crv','brand-honda','CR-V'),
 ('m-honda-civic','brand-honda','Civic'),
 ('m-isuzu-dmax','brand-isuzu','D-Max'),
 ('m-isuzu-mux','brand-isuzu','MU-X'),
 ('m-mazda-bt50','brand-mazda','BT-50'),
 ('m-mazda-cx5','brand-mazda','CX-5'),
 ('m-mazda-mazda3','brand-mazda','Mazda3');

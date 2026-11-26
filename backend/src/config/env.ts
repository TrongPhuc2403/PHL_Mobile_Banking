import dotenv from 'dotenv';

dotenv.config();

export const env = {
  port: Number(process.env.PORT || 8080),
  nodeEnv: process.env.NODE_ENV || 'development',
  db: {
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'phl_mobile_banking',
  },
  jwt: {
    accessSecret: process.env.JWT_ACCESS_SECRET || 'phl_dev_access_secret_key_min_32_characters',
    refreshSecret: process.env.JWT_REFRESH_SECRET || 'phl_dev_refresh_secret_key_min_32_characters',
    accessExpiresIn: process.env.JWT_ACCESS_EXPIRES_IN || '15m',
    refreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '7d',
  },
  security: {
    ekycThresholdVnd: Number(process.env.EKYC_THRESHOLD_VND || 10_000_000),
    otpExpiresMinutes: Number(process.env.OTP_EXPIRES_MINUTES || 2),
    maxLoginAttempts: Number(process.env.MAX_LOGIN_ATTEMPTS || 5),
    lockoutMinutes: Number(process.env.LOCKOUT_MINUTES || 15),
  },
  integrations: {
    geminiApiKey: process.env.GEMINI_API_KEY || '',
    googleMapsApiKey: process.env.GOOGLE_MAPS_API_KEY || '',
  },
};

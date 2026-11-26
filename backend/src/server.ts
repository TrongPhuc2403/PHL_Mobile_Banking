import app from './app';
import { pool } from './config/db';
import { env } from './config/env';

async function bootstrap() {
  try {
    await pool.query('SELECT 1');
    console.log('[DB] Kết nối MySQL phl_mobile_banking thành công');
  } catch (err) {
    console.warn(
      '[DB] Cảnh báo: Chưa kết nối được MySQL, hãy kiểm tra .env và MySQL service:',
      (err as Error).message,
    );
  }

  app.listen(env.port, () => {
    console.log(`[Server] PHL Mobile Banking API đang chạy tại http://localhost:${env.port}`);
  });
}

bootstrap();

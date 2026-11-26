import { spawn } from 'child_process';
import fs from 'fs';
import path from 'path';
import { AppError } from '../utils/errors';

export const faceEngineDirectory = path.resolve(__dirname, '../../face_engine');
let active = 0;
export function modelsReady(): boolean {
  return ['face_detection_yunet_2023mar.onnx', 'face_recognition_sface_2021dec.onnx'].every(
    (name) => fs.existsSync(path.join(faceEngineDirectory, 'models', name)),
  );
}
export async function runFaceEngine(data: Record<string, unknown>): Promise<Record<string, any>> {
  if (!modelsReady())
    throw new AppError(503, 'EKYC_ENGINE_UNAVAILABLE', 'Chưa cài mô hình đối chiếu eKYC.');
  if (active >= 2)
    throw new AppError(429, 'EKYC_BUSY', 'Đang xử lý xác minh khác. Vui lòng thử lại.');
  active++;
  try {
    return await new Promise((resolve, reject) => {
      const child = spawn(
        process.env.EKYC_PYTHON || 'python',
        [path.join(faceEngineDirectory, 'verify.py')],
        {
          stdio: ['pipe', 'pipe', 'pipe'],
          windowsHide: true,
        },
      );
      let output = '';
      const timeout = setTimeout(() => {
        child.kill();
        reject(new AppError(503, 'EKYC_TIMEOUT', 'Đối chiếu quá thời gian. Vui lòng thử lại.'));
      }, 30000);
      child.stdout.on('data', (chunk) => {
        if (output.length < 20000) output += chunk.toString();
      });
      // Do not log images, embeddings, Python stdin, or model internals.
      child.stderr.on('data', () => {});
      child.stdin.on('error', () => {});
      child.once('error', () => {
        clearTimeout(timeout);
        reject(new AppError(503, 'EKYC_ENGINE_UNAVAILABLE', 'Không khởi chạy được Python eKYC.'));
      });
      child.once('close', () => {
        clearTimeout(timeout);
        try {
          const result = JSON.parse(output.trim());
          if (!result.ok)
            reject(
              new AppError(
                result.code === 'EKYC_ENGINE_UNAVAILABLE' ? 503 : 422,
                result.code,
                result.message,
                result.details,
              ),
            );
          else resolve(result);
        } catch {
          reject(
            new AppError(
              503,
              'EKYC_ENGINE_UNAVAILABLE',
              'Dịch vụ đối chiếu không trả kết quả hợp lệ.',
            ),
          );
        }
      });
      child.stdin.end(
        JSON.stringify({ ...data, threshold: Number(process.env.EKYC_COSINE_THRESHOLD || 0.5) }),
      );
    });
  } finally {
    active--;
  }
}

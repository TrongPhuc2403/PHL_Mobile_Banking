import { RowDataPacket } from 'mysql2';
import { pool } from '../config/db';
import { env } from '../config/env';

interface BranchRow extends RowDataPacket {
  id: number;
  branchName: string;
  address: string;
  latitude: number;
  longitude: number;
  isMockData: number;
  phone: string | null;
}

export async function getCustomerNotifications(customerId: number) {
  const [fluctuations] = await pool.query<RowDataPacket[]>(
    `SELECT bf.id, bf.changeAmount, bf.balanceAfter, bf.type, bf.timestamp,
            ba.accountNumber, t.traceId, t.content
     FROM BalanceFluctuation bf
     JOIN BankAccount ba ON ba.id = bf.bankAccountId
     JOIN Transaction t ON t.id = bf.transactionId
     WHERE ba.customerId = ?
     ORDER BY bf.timestamp DESC
     LIMIT 20`,
    [customerId],
  );

  const policyAlerts = [
    {
      id: 'POL-2026-01',
      category: 'POLICY',
      title: 'Quy định xác thực sinh trắc học eKYC',
      message: 'Các giao dịch từ 10.000.000 VNĐ trở lên yêu cầu xác minh khuôn mặt trước bước OTP.',
      publishedAt: '2026-11-01T08:00:00+07:00',
    },
    {
      id: 'POL-2026-02',
      category: 'POLICY',
      title: 'Miễn phí chuyển khoản nhanh Napas 24/7 từ 500.000 VNĐ',
      message: 'Áp dụng cho tất cả tài khoản thanh toán cá nhân tại PHL Bank.',
      publishedAt: '2026-11-10T09:00:00+07:00',
    },
  ];

  return {
    balanceAlerts: fluctuations,
    policyAlerts,
  };
}

function haversineMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export async function findBranchesWithWalkingRoute(userLat?: number, userLng?: number) {
  const [branches] = await pool.query<BranchRow[]>('SELECT * FROM BranchLocation ORDER BY id ASC');

  if (userLat === undefined || userLng === undefined) {
    return { branches, nearest: branches[0] || null, walkingRoute: null };
  }

  const enriched = branches
    .map((b) => {
      const distanceMeters = Math.round(
        haversineMeters(userLat, userLng, Number(b.latitude), Number(b.longitude)),
      );
      return {
        ...b,
        distanceMeters,
        estimatedWalkMinutes: Math.max(1, Math.round(distanceMeters / 80)), // ~4.8 km/h
      };
    })
    .sort((a, b) => a.distanceMeters - b.distanceMeters);

  const nearest = enriched[0] || null;
  const walkingRoute = nearest
    ? {
        mode: 'WALKING',
        distanceMeters: nearest.distanceMeters,
        estimatedMinutes: nearest.estimatedWalkMinutes,
        waypoints: [
          { lat: userLat, lng: userLng },
          {
            lat: Number(((userLat + Number(nearest.latitude)) / 2).toFixed(7)),
            lng: Number(((userLng + Number(nearest.longitude)) / 2).toFixed(7)),
          },
          { lat: Number(nearest.latitude), lng: Number(nearest.longitude) },
        ],
      }
    : null;

  return { branches: enriched, nearest, walkingRoute };
}

export async function askAiAssistant(message: string) {
  const lower = message.toLowerCase();

  let suggestedAction: { label: string; route: string } | null = null;
  let fallbackReply =
    'Tôi có thể hỗ trợ bạn tra cứu số dư, hướng dẫn chuyển khoản, thanh toán hóa đơn hoặc tìm chi nhánh PHL Bank gần nhất.';

  if (lower.includes('chuyển') || lower.includes('gửi tiền')) {
    suggestedAction = { label: 'Mở Chuyển khoản', route: 'phl://transfer' };
    fallbackReply =
      'Để đảm bảo an toàn, bạn vui lòng thực hiện chuyển tiền trực tiếp tại màn hình Chuyển khoản. Lưu ý giao dịch trên 10.000.000 VNĐ cần xác minh eKYC trước khi nhập OTP.';
  } else if (lower.includes('tiết kiệm') || lower.includes('lãi suất')) {
    suggestedAction = { label: 'Xem Tiết kiệm', route: 'phl://savings' };
    fallbackReply =
      'Bạn có thể theo dõi kỳ hạn, lãi suất và tiền lãi dự kiến trong mục Tài khoản tiết kiệm.';
  } else if (lower.includes('vay') || lower.includes('thế chấp') || lower.includes('trả nợ')) {
    suggestedAction = { label: 'Xem Lịch trả nợ', route: 'phl://mortgage' };
    fallbackReply =
      'Lịch trả nợ gốc và lãi từng kỳ được hiển thị chi tiết tại mục Khoản vay thế chấp.';
  } else if (lower.includes('chi nhánh') || lower.includes('đường') || lower.includes('bản đồ')) {
    suggestedAction = { label: 'Tìm chi nhánh gần nhất', route: 'phl://map' };
    fallbackReply =
      'Bạn có thể mở Bản đồ để xem chi nhánh PHL Bank gần nhất và chỉ dẫn đường đi bộ.';
  } else if (
    lower.includes('điện') ||
    lower.includes('nước') ||
    lower.includes('vé') ||
    lower.includes('nạp')
  ) {
    suggestedAction = { label: 'Mở Tiện ích', route: 'phl://utilities' };
    fallbackReply =
      'PHL Bank hỗ trợ thanh toán hóa đơn điện, nước, nạp tiền điện thoại và đặt vé ngay trong mục Tiện ích.';
  }

  if (!env.integrations.geminiApiKey || env.integrations.geminiApiKey.startsWith('your_')) {
    return {
      reply: fallbackReply,
      suggestedAction,
      source: 'LOCAL_BANKING_ASSISTANT',
    };
  }

  try {
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${env.integrations.geminiApiKey}`;
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [
          {
            parts: [
              {
                text:
                  'Bạn là trợ lý ngân hàng PHL Mobile Banking. Trả lời ngắn gọn, lịch sự bằng tiếng Việt, không dùng emoji. Không tự thực hiện chuyển tiền hay sửa số dư. Câu hỏi: ' +
                  message,
              },
            ],
          },
        ],
      }),
    });

    if (response.ok) {
      const json = (await response.json()) as {
        candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
      };
      const aiText = json.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
      if (aiText) {
        return {
          reply: aiText,
          suggestedAction,
          source: 'GEMINI_API',
        };
      }
    }
  } catch {
    // Fallback an toàn nếu mạng hoặc API key không khả dụng
  }

  return {
    reply: fallbackReply,
    suggestedAction,
    source: 'LOCAL_BANKING_ASSISTANT',
  };
}

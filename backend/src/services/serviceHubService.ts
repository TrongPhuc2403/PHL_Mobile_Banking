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

  const [dbPolicyAlerts] = await pool.query<RowDataPacket[]>(
    `SELECT id, category, title, message, isRead, publishedAt
     FROM PolicyNotification
     WHERE customerId = ?
     ORDER BY publishedAt DESC
     LIMIT 10`,
    [customerId],
  );

  const hardcodedPolicyAlerts = [
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

  const policyAlerts = dbPolicyAlerts.length > 0 ? dbPolicyAlerts : hardcodedPolicyAlerts;

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
        googleMapsUrl: `https://www.google.com/maps/dir/?api=1&origin=${userLat},${userLng}&destination=${b.latitude},${b.longitude}&travelmode=walking`,
      };
    })
    .sort((a, b) => a.distanceMeters - b.distanceMeters);

  const nearest = enriched[0] || null;
  const walkingRoute = nearest
    ? {
        mode: 'WALKING',
        algorithm: 'HAVERSINE_SIMULATED',
        note: 'Khoảng cách ước tính theo đường thẳng. Thời gian thực tế phụ thuộc đường đi bộ thực tế.',
        distanceMeters: nearest.distanceMeters,
        estimatedMinutes: nearest.estimatedWalkMinutes,
        waypoints: [
          { lat: userLat, lng: userLng, label: 'Vị trí của bạn' },
          {
            lat: Number(((userLat + Number(nearest.latitude)) / 2).toFixed(7)),
            lng: Number(((userLng + Number(nearest.longitude)) / 2).toFixed(7)),
            label: 'Điểm giữa đường',
          },
          {
            lat: Number(nearest.latitude),
            lng: Number(nearest.longitude),
            label: nearest.branchName,
          },
        ],
        googleMapsUrl: nearest.googleMapsUrl,
      }
    : null;

  return { branches: enriched, nearest, walkingRoute };
}

export type ActionToken =
  | '[ACTION_TRANSFER]'
  | '[ACTION_PAY_BILL]'
  | '[ACTION_TOPUP]'
  | '[ACTION_HISTORY]'
  | '[ACTION_SAVINGS]'
  | '[ACTION_MORTGAGE]'
  | '[ACTION_MAP]'
  | '[ACTION_PROFILE]';

export interface ActionSuggestion {
  label: string;
  route: string;
  actionToken: ActionToken;
}

const ACTION_CATALOG: Record<ActionToken, ActionSuggestion> = {
  '[ACTION_TRANSFER]': {
    label: 'Mở Chuyển khoản',
    route: 'phl://transfer',
    actionToken: '[ACTION_TRANSFER]',
  },
  '[ACTION_PAY_BILL]': {
    label: 'Thanh toán hóa đơn',
    route: 'phl://utilities/bill',
    actionToken: '[ACTION_PAY_BILL]',
  },
  '[ACTION_TOPUP]': {
    label: 'Nạp tiền điện thoại',
    route: 'phl://utilities/topup',
    actionToken: '[ACTION_TOPUP]',
  },
  '[ACTION_HISTORY]': {
    label: 'Xem Lịch sử giao dịch',
    route: 'phl://history',
    actionToken: '[ACTION_HISTORY]',
  },
  '[ACTION_SAVINGS]': {
    label: 'Xem Tiết kiệm',
    route: 'phl://savings',
    actionToken: '[ACTION_SAVINGS]',
  },
  '[ACTION_MORTGAGE]': {
    label: 'Xem Lịch trả nợ',
    route: 'phl://mortgage',
    actionToken: '[ACTION_MORTGAGE]',
  },
  '[ACTION_MAP]': {
    label: 'Tìm chi nhánh gần nhất',
    route: 'phl://map',
    actionToken: '[ACTION_MAP]',
  },
  '[ACTION_PROFILE]': {
    label: 'Mở Hồ sơ cá nhân',
    route: 'phl://profile',
    actionToken: '[ACTION_PROFILE]',
  },
};

const ACTION_TOKEN_REGEX =
  /\[(ACTION_TRANSFER|ACTION_PAY_BILL|ACTION_TOPUP|ACTION_HISTORY|ACTION_SAVINGS|ACTION_MORTGAGE|ACTION_MAP|ACTION_PROFILE)\]/;

export async function askAiAssistant(message: string) {
  const lower = message.toLowerCase();

  let suggestedAction: ActionSuggestion | null = null;
  let fallbackReply =
    'Tôi có thể hỗ trợ bạn tra cứu số dư, hướng dẫn chuyển khoản, thanh toán hóa đơn, nạp tiền điện thoại, xem lịch sử giao dịch hoặc tìm chi nhánh PHL Bank gần nhất.';

  if (
    lower.includes('lịch sử') ||
    lower.includes('giao dịch') ||
    lower.includes('tháng này') ||
    lower.includes('sao kê')
  ) {
    suggestedAction = ACTION_CATALOG['[ACTION_HISTORY]'];
    fallbackReply =
      'Bạn có thể xem chi tiết biến động số dư và lịch sử giao dịch theo từng tài khoản trong mục Lịch sử giao dịch.';
  } else if (lower.includes('chuyển') || lower.includes('gửi tiền')) {
    suggestedAction = ACTION_CATALOG['[ACTION_TRANSFER]'];
    fallbackReply =
      'Để đảm bảo an toàn, bạn vui lòng thực hiện chuyển tiền trực tiếp tại màn hình Chuyển khoản. Lưu ý giao dịch trên 10.000.000 VNĐ cần xác minh eKYC trước khi nhập OTP.';
  } else if (
    lower.includes('nạp') &&
    (lower.includes('điện thoại') || lower.includes('thẻ') || lower.includes('di động'))
  ) {
    suggestedAction = ACTION_CATALOG['[ACTION_TOPUP]'];
    fallbackReply =
      'Bạn có thể nạp tiền điện thoại nhanh chóng cho các nhà mạng ngay trong mục Nạp tiền điện thoại.';
  } else if (
    lower.includes('hóa đơn') ||
    lower.includes('điện') ||
    lower.includes('nước') ||
    lower.includes('vé')
  ) {
    suggestedAction = ACTION_CATALOG['[ACTION_PAY_BILL]'];
    fallbackReply =
      'PHL Bank hỗ trợ thanh toán hóa đơn điện, nước và các tiện ích khác ngay trong mục Thanh toán hóa đơn.';
  } else if (lower.includes('tiết kiệm') || lower.includes('lãi suất')) {
    suggestedAction = ACTION_CATALOG['[ACTION_SAVINGS]'];
    fallbackReply =
      'Bạn có thể theo dõi kỳ hạn, lãi suất và tiền lãi dự kiến trong mục Tài khoản tiết kiệm.';
  } else if (lower.includes('vay') || lower.includes('thế chấp') || lower.includes('trả nợ')) {
    suggestedAction = ACTION_CATALOG['[ACTION_MORTGAGE]'];
    fallbackReply =
      'Lịch trả nợ gốc và lãi từng kỳ được hiển thị chi tiết tại mục Khoản vay thế chấp.';
  } else if (lower.includes('chi nhánh') || lower.includes('đường') || lower.includes('bản đồ')) {
    suggestedAction = ACTION_CATALOG['[ACTION_MAP]'];
    fallbackReply =
      'Bạn có thể mở Bản đồ để xem chi nhánh PHL Bank gần nhất và chỉ dẫn đường đi bộ.';
  } else if (
    lower.includes('hồ sơ') ||
    lower.includes('cá nhân') ||
    lower.includes('đổi mật khẩu') ||
    lower.includes('ekyc') ||
    lower.includes('cccd')
  ) {
    suggestedAction = ACTION_CATALOG['[ACTION_PROFILE]'];
    fallbackReply =
      'Bạn có thể xem và cập nhật thông tin liên hệ, bảo mật hoặc trạng thái eKYC tại mục Hồ sơ cá nhân.';
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
                  'Bạn là trợ lý ngân hàng PHL Mobile Banking. Trả lời ngắn gọn, lịch sự bằng tiếng Việt, không dùng emoji. Không tự thực hiện chuyển tiền hay sửa số dư. Nếu câu hỏi liên quan đến một chức năng trong app, hãy thêm đúng 1 mã token phù hợp ở cuối câu trả lời trong danh sách: [ACTION_TRANSFER], [ACTION_PAY_BILL], [ACTION_TOPUP], [ACTION_HISTORY], [ACTION_SAVINGS], [ACTION_MORTGAGE], [ACTION_MAP], [ACTION_PROFILE]. Câu hỏi: ' +
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
      const rawAiText = json.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
      if (rawAiText) {
        const tokenMatch = rawAiText.match(ACTION_TOKEN_REGEX);
        const extractedToken = tokenMatch?.[0] as ActionToken | undefined;
        const resolvedAction = extractedToken ? ACTION_CATALOG[extractedToken] : suggestedAction;
        const cleanReply = rawAiText.replace(ACTION_TOKEN_REGEX, '').trim() || fallbackReply;

        return {
          reply: cleanReply,
          suggestedAction: resolvedAction,
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

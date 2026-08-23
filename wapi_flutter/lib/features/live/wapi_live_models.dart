const wapiLiveReactionGlyphs = <String, String>{
  'heart': '❤️',
  'applause': '👏',
  'fire': '🔥',
  'wow': '🤩',
};

String wapiLiveReactionGlyph(String reaction) =>
    wapiLiveReactionGlyphs[reaction] ?? wapiLiveReactionGlyphs['heart']!;

class WapiLiveListing {
  const WapiLiveListing({
    required this.id,
    required this.hostId,
    required this.hostName,
    required this.hostPhotoUrl,
    required this.title,
    required this.category,
    required this.visibility,
    required this.status,
    required this.viewerCount,
    required this.reactionCount,
    required this.aiGenerated,
  });

  factory WapiLiveListing.fromMap(Map<String, dynamic> data) => WapiLiveListing(
    id: data['id'] as String? ?? '',
    hostId: data['hostId'] as String? ?? '',
    hostName: data['hostName'] as String? ?? 'Créateur WAPI',
    hostPhotoUrl: data['hostPhotoUrl'] as String? ?? '',
    title: data['title'] as String? ?? 'Live WAPI',
    category: data['category'] as String? ?? 'Discussion',
    visibility: data['visibility'] as String? ?? 'public',
    status: data['status'] as String? ?? 'scheduled',
    viewerCount: (data['viewerCount'] as num?)?.toInt() ?? 0,
    reactionCount: (data['reactionCount'] as num?)?.toInt() ?? 0,
    aiGenerated: data['aiGenerated'] == true,
  );

  final String id;
  final String hostId;
  final String hostName;
  final String hostPhotoUrl;
  final String title;
  final String category;
  final String visibility;
  final String status;
  final int viewerCount;
  final int reactionCount;
  final bool aiGenerated;

  bool get isLive => status == 'live';
}

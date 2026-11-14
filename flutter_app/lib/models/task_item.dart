class TaskItem {
  final String id;
  final String title;
  final String subject;
  final String date;
  bool isCompleted;
  final int priority;

  TaskItem({
    required this.id,
    required this.title,
    required this.subject,
    required this.date,
    this.isCompleted = false,
    this.priority = 1,
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'subject': subject,
        'date': date,
        'isCompleted': isCompleted,
        'priority': priority,
      };

  factory TaskItem.fromJson(Map<String, dynamic> json) => TaskItem(
        id: json['id'] ?? '',
        title: json['title'] ?? '',
        subject: json['subject'] ?? '',
        date: json['date'] ?? '',
        isCompleted: json['isCompleted'] ?? false,
        priority: json['priority'] ?? 1,
      );
}

class CloudMediaFile {
  final int id;
  final String fileId;
  final String title;
  final String fileType; // pdf, audio, video, image, document
  final String category; // quran, azkar, adhan, study, lecture
  final String caption;
  final String createdAt;

  CloudMediaFile({
    required this.id,
    required this.fileId,
    required this.title,
    required this.fileType,
    required this.category,
    required this.caption,
    required this.createdAt,
  });

  factory CloudMediaFile.fromJson(Map<String, dynamic> json) => CloudMediaFile(
        id: json['id'] ?? 0,
        fileId: json['file_id'] ?? '',
        title: json['file_name'] ?? json['caption'] ?? 'ملف سحابي',
        fileType: json['file_type'] ?? 'document',
        category: json['category'] ?? 'general',
        caption: json['caption'] ?? '',
        createdAt: json['created_at'] ?? '',
      );
}

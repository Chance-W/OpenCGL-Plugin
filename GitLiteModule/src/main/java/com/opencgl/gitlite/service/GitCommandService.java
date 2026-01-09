package com.opencgl.gitlite.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Git 命令服务
 */
public class GitCommandService {
    private final java.util.Set<Process> processes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private Process track(Process process) {
        processes.add(process);
        process.onExit().thenRun(() -> processes.remove(process));
        return process;
    }

    /**
     * 获取最近提交记录
     */
    public List<CommitInfo> getRecentCommits(String repoPath, int count) {
        List<CommitInfo> commits = new ArrayList<>();

        try {
            ProcessBuilder pb = new ProcessBuilder("git", "log",
                    "--pretty=format:%H|%h|%an|%ae|%ad|%s",
                    "--date=iso", "-n", String.valueOf(count));
            pb.directory(new File(repoPath));
            Process process = track(pb.start());

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|", 6);
                    if (parts.length >= 6) {
                        commits.add(new CommitInfo(
                                parts[0], // full hash
                                parts[1], // short hash
                                parts[2], // author
                                parts[3], // email
                                parts[4], // date
                                parts[5] // message
                        ));
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            // 返回空列表
        }

        return commits;
    }

    /**
     * 检查是否是 Git 仓库
     */
    public boolean isGitRepo(String path) {
        File gitDir = new File(path, ".git");
        return gitDir.exists() && gitDir.isDirectory();
    }

    /**
     * 获取当前分支
     */
    public String getCurrentBranch(String repoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "branch", "--show-current");
            pb.directory(new File(repoPath));
            Process process = track(pb.start());

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine();
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    public void dispose() {
        processes.forEach(Process::destroyForcibly);
        processes.clear();
    }

    /**
     * Git 命令速查
     */
    public static final List<CommandCategory> COMMAND_CHEATSHEET = List.of(
            new CommandCategory("cat.basic", List.of(
                    new GitCommand("git init", "cmd.init.name", "cmd.init.desc"),
                    new GitCommand("git clone <url>", "cmd.clone.name", "cmd.clone.desc"),
                    new GitCommand("git status", "cmd.status.name", "cmd.status.desc"),
                    new GitCommand("git add <file>", "cmd.add_file.name", "cmd.add_file.desc"),
                    new GitCommand("git add .", "cmd.add_all.name", "cmd.add_all.desc"),
                    new GitCommand("git commit -m \"msg\"", "cmd.commit.name", "cmd.commit.desc"),
                    new GitCommand("git commit -am \"msg\"", "cmd.commit_am.name", "cmd.commit_am.desc"))),
            new CommandCategory("cat.branch", List.of(
                    new GitCommand("git branch", "cmd.branch_list.name", "cmd.branch_list.desc"),
                    new GitCommand("git branch <name>", "cmd.branch_create.name", "cmd.branch_create.desc"),
                    new GitCommand("git checkout <branch>", "cmd.checkout.name", "cmd.checkout.desc"),
                    new GitCommand("git checkout -b <name>", "cmd.checkout_b.name", "cmd.checkout_b.desc"),
                    new GitCommand("git merge <branch>", "cmd.merge.name", "cmd.merge.desc"),
                    new GitCommand("git branch -d <name>", "cmd.branch_delete.name", "cmd.branch_delete.desc"),
                    new GitCommand("git branch -D <name>", "cmd.branch_delete_force.name",
                            "cmd.branch_delete_force.desc"))),
            new CommandCategory("cat.remote", List.of(
                    new GitCommand("git remote -v", "cmd.remote_v.name", "cmd.remote_v.desc"),
                    new GitCommand("git remote add origin <url>", "cmd.remote_add.name", "cmd.remote_add.desc"),
                    new GitCommand("git push", "cmd.push.name", "cmd.push.desc"),
                    new GitCommand("git push -u origin <branch>", "cmd.push_u.name", "cmd.push_u.desc"),
                    new GitCommand("git pull", "cmd.pull.name", "cmd.pull.desc"),
                    new GitCommand("git fetch", "cmd.fetch.name", "cmd.fetch.desc"))),
            new CommandCategory("cat.history", List.of(
                    new GitCommand("git log", "cmd.log.name", "cmd.log.desc"),
                    new GitCommand("git log --oneline", "cmd.log_oneline.name", "cmd.log_oneline.desc"),
                    new GitCommand("git log --graph", "cmd.log_graph.name", "cmd.log_graph.desc"),
                    new GitCommand("git diff", "cmd.diff.name", "cmd.diff.desc"),
                    new GitCommand("git diff --staged", "cmd.diff_staged.name", "cmd.diff_staged.desc"),
                    new GitCommand("git show <commit>", "cmd.show.name", "cmd.show.desc"))),
            new CommandCategory("cat.undo", List.of(
                    new GitCommand("git checkout -- <file>", "cmd.checkout_undo.name", "cmd.checkout_undo.desc"),
                    new GitCommand("git reset HEAD <file>", "cmd.reset_head.name", "cmd.reset_head.desc"),
                    new GitCommand("git reset --soft HEAD~1", "cmd.reset_soft.name", "cmd.reset_soft.desc"),
                    new GitCommand("git reset --hard HEAD~1", "cmd.reset_hard.name", "cmd.reset_hard.desc"),
                    new GitCommand("git revert <commit>", "cmd.revert.name", "cmd.revert.desc"),
                    new GitCommand("git stash", "cmd.stash.name", "cmd.stash.desc"),
                    new GitCommand("git stash pop", "cmd.stash_pop.name", "cmd.stash_pop.desc"))),
            new CommandCategory("cat.tag", List.of(
                    new GitCommand("git tag", "cmd.tag_list.name", "cmd.tag_list.desc"),
                    new GitCommand("git tag <name>", "cmd.tag_create.name", "cmd.tag_create.desc"),
                    new GitCommand("git tag -a <name> -m \"msg\"", "cmd.tag_annotated.name", "cmd.tag_annotated.desc"),
                    new GitCommand("git push origin <tag>", "cmd.tag_push.name", "cmd.tag_push.desc"),
                    new GitCommand("git push origin --tags", "cmd.tag_push_all.name", "cmd.tag_push_all.desc"))));

    public record CommitInfo(String hash, String shortHash, String author, String email, String date, String message) {
    }

    public record GitCommand(String command, String name, String description) {
    }

    public record CommandCategory(String name, List<GitCommand> commands) {
    }
}

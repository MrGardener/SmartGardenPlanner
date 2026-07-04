import os
import glob
import subprocess

# Dictionary mapping unique phrases in the files to their proper filenames
FILE_MAPPINGS = {
    "MASTER MASTER PLAN": "smart_garden_planner_master_plan.md",
    "THE ARCHITECTURAL TRANSCRIPT": "smart_garden_planner_transcript.md",
    "| T2 Product Requirement": "smart_garden_planner_traceability_t2_t3.md",
    "| T3 High-Level Requirement": "smart_garden_planner_traceability_t3_t4.md",
    "11-Perspective Review Board Audit": "smart_garden_planner_audit_report.md",
    "| ID | Category | Type | Description": "smart_garden_planner_risk_register.md",
    "AUTOMATED SYSTEM HANDOFF RUNTIME SCRIPT": "smart_garden_planner_automated_handoff.md"
}

def rename_files():
    # Find all files starting with 'gemini-code-' and ending in '.md'
    target_files = glob.glob("gemini-code-*.md")
    
    if not target_files:
        print("No gemini-code files found to rename.")
        return

    for filename in target_files:
        try:
            with open(filename, 'r', encoding='utf-8') as file:
                head = "".join([next(file, "") for _ in range(10)])
                
            for key_phrase, new_name in FILE_MAPPINGS.items():
                if key_phrase in head:
                    if os.path.exists(new_name):
                        os.remove(new_name)
                    os.rename(filename, new_name)
                    print(f"✅ Renamed: {filename} -> {new_name}")
                    break
        except Exception as e:
            print(f"❌ Error processing {filename}: {e}")

def git_push_updates():
    print("\n🚀 Commencing Git synchronization...")
    try:
        # 1. Add all project files
        subprocess.run(["git", "add", "smart_garden_planner_master_plan.md", 
                        "smart_garden_planner_transcript.md", 
                        "smart_garden_planner_traceability_t2_t3.md",
                        "smart_garden_planner_traceability_t3_t4.md",
                        "smart_garden_planner_audit_report.md",
                        "smart_garden_planner_risk_register.md",
                        "smart_garden_planner_automated_handoff.md", 
                        "session_restart_prompt.txt"], check=True)
        
        # 2. Commit the changes
        subprocess.run(["git", "commit", "-m", "Sealed version development footprint v20.18"], check=True)
        
        # 3. Push to remote
        subprocess.run(["git", "push"], check=True)
        print("✅ Successfully pushed to GitHub.")
        
    except subprocess.CalledProcessError as e:
        print(f"❌ Git synchronization failed: {e}")

if __name__ == "__main__":
    rename_files()
    git_push_updates()
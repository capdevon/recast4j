package org.recast4j.demo.tool;

import static org.lwjgl.nuklear.Nuklear.NK_TEXT_ALIGN_LEFT;
import static org.lwjgl.nuklear.Nuklear.nk_label;
import static org.lwjgl.nuklear.Nuklear.nk_layout_row_dynamic;
import static org.lwjgl.nuklear.Nuklear.nk_option_label;
import static org.lwjgl.nuklear.Nuklear.nk_spacing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.nuklear.NkContext;
import org.recast4j.demo.builder.SampleAreaModifications;
import org.recast4j.demo.draw.DebugDraw;
import org.recast4j.demo.draw.DebugDrawPrimitives;
import org.recast4j.demo.draw.NavMeshRenderer;
import org.recast4j.demo.draw.RecastDebugDraw;
import org.recast4j.demo.math.DemoMath;
import org.recast4j.demo.sample.Sample;
import org.recast4j.detour.DefaultQueryFilter;
import org.recast4j.detour.FindDistanceToWallResult;
import org.recast4j.detour.FindLocalNeighbourhoodResult;
import org.recast4j.detour.FindPolysAroundResult;
import org.recast4j.detour.GetPolyWallSegmentsResult;
import org.recast4j.detour.MeshTile;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshQuery;
import org.recast4j.detour.Poly;
import org.recast4j.detour.RaycastHit;
import org.recast4j.detour.StraightPathItem;
import org.recast4j.detour.Tupple2;

public class TestNavmeshTool implements Tool {

    private final static int MAX_POLYS = 256;
    private final static int MAX_SMOOTH = 2048;
    private Sample m_sample;
    private ToolMode m_toolMode = ToolMode.PATHFIND_FOLLOW;
    private boolean m_sposSet;
    private boolean m_eposSet;
    private float[] m_spos;
    private float[] m_epos;
    private final DefaultQueryFilter m_filter;
    private final float[] m_polyPickExt = new float[] { 2, 4, 2 };
    private long m_startRef;
    private long m_endRef;
    private float[] m_hitPos;
    private float m_distanceToWall;
    private float[] m_hitNormal;
    private List<StraightPathItem> m_straightPath;
    private int m_straightPathOptions;
    private List<Long> m_polys;
    private boolean m_hitResult;
    private boolean m_nsmoothPath;
    private List<Long> m_parent;
    private float m_neighbourhoodRadius;
    private final float[] m_queryPoly = new float[12];

    private enum ToolMode {
        PATHFIND_FOLLOW, PATHFIND_STRAIGHT, PATHFIND_SLICED, DISTANCE_TO_WALL, RAYCAST, FIND_POLYS_IN_CIRCLE, FIND_POLYS_IN_SHAPE, FIND_LOCAL_NEIGHBOURHOOD
    }

    public TestNavmeshTool() {
        m_filter = new DefaultQueryFilter(SampleAreaModifications.SAMPLE_POLYFLAGS_ALL ^ SampleAreaModifications.SAMPLE_POLYFLAGS_DISABLED,
                0, new float[] { 1f, 10f, 1f, 1f, 2f, 1.5f });
    }

    @Override
    public void setSample(Sample m_sample) {
        this.m_sample = m_sample;
    }

    @Override
    public void handleClick(float[] s, float[] p, boolean shift) {
        if (shift) {
            m_sposSet = true;
            m_spos = Arrays.copyOf(p, p.length);
        } else {
            m_eposSet = true;
            m_epos = Arrays.copyOf(p, p.length);
        }
        recalc();
    }

    @Override
    public void layout(NkContext ctx) {
        ToolMode previousToolMode = m_toolMode;
        int previousStraightPathOptions = m_straightPathOptions;
        int previousIncludeFlags = m_filter.getIncludeFlags();

//        nk_layout_row_dynamic(ctx, 20, 1);
//        if (nk_option_label(ctx, "Pathfind Follow", m_toolMode == ToolMode.PATHFIND_FOLLOW)) {
//            m_toolMode = ToolMode.PATHFIND_FOLLOW;
//        }
        nk_layout_row_dynamic(ctx, 20, 1);
        if (nk_option_label(ctx, "Pathfind Straight", m_toolMode == ToolMode.PATHFIND_STRAIGHT)) {
            m_toolMode = ToolMode.PATHFIND_STRAIGHT;
            nk_layout_row_dynamic(ctx, 20, 1);
            nk_label(ctx, "Vertices at crossings", NK_TEXT_ALIGN_LEFT);
            nk_layout_row_dynamic(ctx, 20, 1);
            if (nk_option_label(ctx, "None", m_straightPathOptions == 0)) {
                m_straightPathOptions = 0;
            }
            nk_layout_row_dynamic(ctx, 20, 1);
            if (nk_option_label(ctx, "Area", m_straightPathOptions == NavMeshQuery.DT_STRAIGHTPATH_AREA_CROSSINGS)) {
                m_straightPathOptions = NavMeshQuery.DT_STRAIGHTPATH_AREA_CROSSINGS;
            }
            nk_layout_row_dynamic(ctx, 20, 1);
            if (nk_option_label(ctx, "All", m_straightPathOptions == NavMeshQuery.DT_STRAIGHTPATH_ALL_CROSSINGS)) {
                m_straightPathOptions = NavMeshQuery.DT_STRAIGHTPATH_ALL_CROSSINGS;
            }
            nk_layout_row_dynamic(ctx, 5, 1);
            nk_spacing(ctx, 1);
        }
//        nk_layout_row_dynamic(ctx, 20, 1);
//        if (nk_option_label(ctx, "Pathfind Sliced", m_toolMode == ToolMode.PATHFIND_SLICED)) {
//            m_toolMode = ToolMode.PATHFIND_SLICED;
//        }
        nk_layout_row_dynamic(ctx, 5, 1);
        nk_spacing(ctx, 1);
        nk_layout_row_dynamic(ctx, 20, 1);
        if (nk_option_label(ctx, "Distance to Wall", m_toolMode == ToolMode.DISTANCE_TO_WALL)) {
            m_toolMode = ToolMode.DISTANCE_TO_WALL;
        }
        nk_layout_row_dynamic(ctx, 5, 1);
        nk_spacing(ctx, 1);
        nk_layout_row_dynamic(ctx, 20, 1);
        if (nk_option_label(ctx, "Raycast", m_toolMode == ToolMode.RAYCAST)) {
            m_toolMode = ToolMode.RAYCAST;
        }
        nk_layout_row_dynamic(ctx, 5, 1);
        nk_spacing(ctx, 1);
        nk_layout_row_dynamic(ctx, 20, 1);
        if (nk_option_label(ctx, "Find Polys in Circle", m_toolMode == ToolMode.FIND_POLYS_IN_CIRCLE)) {
            m_toolMode = ToolMode.FIND_POLYS_IN_CIRCLE;
        }
        if (nk_option_label(ctx, "Find Polys in Shape", m_toolMode == ToolMode.FIND_POLYS_IN_SHAPE)) {
            m_toolMode = ToolMode.FIND_POLYS_IN_SHAPE;
        }
        nk_layout_row_dynamic(ctx, 5, 1);
        nk_spacing(ctx, 1);
        nk_layout_row_dynamic(ctx, 20, 1);
        if (nk_option_label(ctx, "Find Local Neighbourhood", m_toolMode == ToolMode.FIND_LOCAL_NEIGHBOURHOOD)) {
            m_toolMode = ToolMode.FIND_LOCAL_NEIGHBOURHOOD;
        }

        nk_layout_row_dynamic(ctx, 5, 1);
        nk_spacing(ctx, 1);
        nk_layout_row_dynamic(ctx, 20, 1);
        nk_label(ctx, "Include Flags", NK_TEXT_ALIGN_LEFT);
        nk_layout_row_dynamic(ctx, 20, 1);
        int includeFlags = 0;
        if (nk_option_label(ctx, "Walk", (m_filter.getIncludeFlags() & SampleAreaModifications.SAMPLE_POLYFLAGS_WALK) != 0)) {
            includeFlags |= SampleAreaModifications.SAMPLE_POLYFLAGS_WALK;
        }
        if (nk_option_label(ctx, "Swim", (m_filter.getIncludeFlags() & SampleAreaModifications.SAMPLE_POLYFLAGS_SWIM) != 0)) {
            includeFlags |= SampleAreaModifications.SAMPLE_POLYFLAGS_SWIM;
        }
        if (nk_option_label(ctx, "Door", (m_filter.getIncludeFlags() & SampleAreaModifications.SAMPLE_POLYFLAGS_DOOR) != 0)) {
            includeFlags |= SampleAreaModifications.SAMPLE_POLYFLAGS_DOOR;
        }
        if (nk_option_label(ctx, "Jump", (m_filter.getIncludeFlags() & SampleAreaModifications.SAMPLE_POLYFLAGS_JUMP) != 0)) {
            includeFlags |= SampleAreaModifications.SAMPLE_POLYFLAGS_JUMP;
        }
        m_filter.setIncludeFlags(includeFlags);
        if (previousToolMode != m_toolMode || m_straightPathOptions != previousStraightPathOptions
                || previousIncludeFlags != includeFlags) {
            recalc();
        }
    }

    @Override
    public String getName() {
        return "Test Navmesh";
    }

    private void recalc() {
        if (m_sample == null || m_sample.getNavMesh() == null) {
            return;
        }
        if (m_sposSet) {
            m_startRef = m_sample.getNavMeshQuery().findNearestPoly(m_spos, m_polyPickExt, m_filter).getNearestRef();
        } else {
            m_startRef = 0;
        }
        if (m_eposSet) {
            m_endRef = m_sample.getNavMeshQuery().findNearestPoly(m_epos, m_polyPickExt, m_filter).getNearestRef();
        } else {
            m_endRef = 0;
        }
        NavMeshQuery m_navQuery = m_sample.getNavMeshQuery();
        if (m_toolMode == ToolMode.PATHFIND_STRAIGHT) {
            if (m_sposSet && m_eposSet && m_startRef != 0 && m_endRef != 0) {
                m_polys = m_navQuery.findPath(m_startRef, m_endRef, m_spos, m_epos, m_filter).getRefs();
                if (!m_polys.isEmpty()) {
                    // In case of partial path, make sure the end point is clamped to the last polygon.
                    float[] epos = new float[] { m_epos[0], m_epos[1], m_epos[2] };
                    if (m_polys.get(m_polys.size() - 1) != m_endRef) {
                        epos = m_navQuery.closestPointOnPoly(m_polys.get(m_polys.size() - 1), m_epos).getClosest();
                    }
                    m_straightPath = m_navQuery.findStraightPath(m_spos, epos, m_polys, MAX_POLYS, m_straightPathOptions);
                }
            } else {
                m_straightPath = null;
            }
        } else if (m_toolMode == ToolMode.RAYCAST) {
            m_straightPath = null;
            if (m_sposSet && m_eposSet && m_startRef != 0) {
                {
                    RaycastHit hit = m_navQuery.raycast(m_startRef, m_spos, m_epos, m_filter, 0, 0);
                    m_polys = hit.path;
                    if (hit.t > 1) {
                        // No hit
                        m_hitPos = Arrays.copyOf(m_epos, m_epos.length);
                        m_hitResult = false;
                    } else {
                        // Hit
                        m_hitPos = DemoMath.vLerp(m_spos, m_epos, hit.t);
                        m_hitNormal = Arrays.copyOf(hit.hitNormal, hit.hitNormal.length);
                        m_hitResult = true;
                    }
                    // Adjust height.
                    if (hit.path.size() > 0) {
                        m_hitPos[1] = m_navQuery.getPolyHeight(hit.path.get(hit.path.size() - 1), m_hitPos);
                    }
                    m_straightPath = new ArrayList<>();
                    m_straightPath.add(new StraightPathItem(m_spos, 0, 0));
                    m_straightPath.add(new StraightPathItem(m_hitPos, 0, 0));
                }
            }
        } else if (m_toolMode == ToolMode.DISTANCE_TO_WALL) {
            m_distanceToWall = 0;
            if (m_sposSet && m_startRef != 0) {
                m_distanceToWall = 0.0f;
                FindDistanceToWallResult result = m_navQuery.findDistanceToWall(m_startRef, m_spos, 100.0f, m_filter);
                m_distanceToWall = result.getDistance();
                m_hitPos = result.getPosition();
                m_hitNormal = result.getNormal();
            }
        } else if (m_toolMode == ToolMode.FIND_POLYS_IN_CIRCLE) {
            if (m_sposSet && m_startRef != 0 && m_eposSet) {
                float dx = m_epos[0] - m_spos[0];
                float dz = m_epos[2] - m_spos[2];
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                FindPolysAroundResult result = m_navQuery.findPolysAroundCircle(m_startRef, m_spos, dist, m_filter);
                m_polys = result.getRefs();
                m_parent = result.getParentRefs();
            }
        } else if (m_toolMode == ToolMode.FIND_POLYS_IN_SHAPE) {
            if (m_sposSet && m_startRef != 0 && m_eposSet) {
                float nx = (m_epos[2] - m_spos[2]) * 0.25f;
                float nz = -(m_epos[0] - m_spos[0]) * 0.25f;
                float agentHeight = m_sample != null ? m_sample.getSettingsUI().getAgentHeight() : 0;

                m_queryPoly[0] = m_spos[0] + nx * 1.2f;
                m_queryPoly[1] = m_spos[1] + agentHeight / 2;
                m_queryPoly[2] = m_spos[2] + nz * 1.2f;

                m_queryPoly[3] = m_spos[0] - nx * 1.3f;
                m_queryPoly[4] = m_spos[1] + agentHeight / 2;
                m_queryPoly[5] = m_spos[2] - nz * 1.3f;

                m_queryPoly[6] = m_epos[0] - nx * 0.8f;
                m_queryPoly[7] = m_epos[1] + agentHeight / 2;
                m_queryPoly[8] = m_epos[2] - nz * 0.8f;

                m_queryPoly[9] = m_epos[0] + nx;
                m_queryPoly[10] = m_epos[1] + agentHeight / 2;
                m_queryPoly[11] = m_epos[2] + nz;

                FindPolysAroundResult result = m_navQuery.findPolysAroundShape(m_startRef, m_queryPoly, 4, m_filter);
                m_polys = result.getRefs();
                m_parent = result.getParentRefs();
            }
        } else if (m_toolMode == ToolMode.FIND_LOCAL_NEIGHBOURHOOD) {
            if (m_sposSet && m_startRef != 0) {
                m_neighbourhoodRadius = m_sample.getSettingsUI().getAgentRadius() * 20.0f;
                FindLocalNeighbourhoodResult result = m_navQuery.findLocalNeighbourhood(m_startRef, m_spos, m_neighbourhoodRadius,
                        m_filter);
                m_polys = result.getRefs();
                m_parent = result.getParentRefs();
            }
        }
    }

    @Override
    public void handleRender(NavMeshRenderer renderer) {
        if (m_sample == null) {
            return;
        }
        RecastDebugDraw dd = renderer.getDebugDraw();
        int startCol = DebugDraw.duRGBA(128, 25, 0, 192);
        int endCol = DebugDraw.duRGBA(51, 102, 0, 129);
        int pathCol = DebugDraw.duRGBA(0, 0, 0, 64);

        float agentRadius = m_sample.getSettingsUI().getAgentRadius();
        float agentHeight = m_sample.getSettingsUI().getAgentHeight();
        float agentClimb = m_sample.getSettingsUI().getAgentMaxClimb();

        if (m_sposSet) {
            drawAgent(dd, m_spos, agentRadius, agentHeight, agentClimb, startCol);
        }
        if (m_eposSet) {
            drawAgent(dd, m_epos, agentRadius, agentHeight, agentClimb, endCol);
        }
        dd.depthMask(true);

        NavMesh m_navMesh = m_sample.getNavMesh();
        if (m_navMesh == null) {
            return;
        }

        if (m_toolMode == ToolMode.PATHFIND_FOLLOW) {
            dd.debugDrawNavMeshPoly(m_navMesh, m_startRef, startCol);
            dd.debugDrawNavMeshPoly(m_navMesh, m_endRef, endCol);

            if (m_polys != null) {
                for (Long poly : m_polys) {
                    if (poly == m_startRef || poly == m_endRef) {
                        continue;
                    }
                    dd.debugDrawNavMeshPoly(m_navMesh, poly, pathCol);
                }
            }
            /*
            if (m_nsmoothPath != null)
            {
                dd.depthMask(false);
                int spathCol = DebugDraw.duRGBA(0,0,0,220);
                dd.begin(DebugDrawPrimitives.LINES, 3.0f);
                for (int i = 0; i < m_nsmoothPath; ++i) {
                    dd.vertex(m_smoothPath[i*3], m_smoothPath[i*3+1]+0.1f, m_smoothPath[i*3+2], spathCol);
                }
                dd.end();
                dd.depthMask(true);
            }
            if (m_pathIterNum)
            {
                duDebugDrawNavMeshPoly(&dd, *m_navMesh, m_pathIterPolys[0], DebugDraw.duRGBA(255,255,255,128));

                dd.depthMask(false);
                dd.begin(DebugDrawPrimitives.LINES, 1.0f);

                int prevCol = DebugDraw.duRGBA(255,192,0,220);
                int curCol = DebugDraw.duRGBA(255,255,255,220);
                int steerCol = DebugDraw.duRGBA(0,192,255,220);

                dd.vertex(m_prevIterPos[0],m_prevIterPos[1]-0.3f,m_prevIterPos[2], prevCol);
                dd.vertex(m_prevIterPos[0],m_prevIterPos[1]+0.3f,m_prevIterPos[2], prevCol);

                dd.vertex(m_iterPos[0],m_iterPos[1]-0.3f,m_iterPos[2], curCol);
                dd.vertex(m_iterPos[0],m_iterPos[1]+0.3f,m_iterPos[2], curCol);

                dd.vertex(m_prevIterPos[0],m_prevIterPos[1]+0.3f,m_prevIterPos[2], prevCol);
                dd.vertex(m_iterPos[0],m_iterPos[1]+0.3f,m_iterPos[2], prevCol);

                dd.vertex(m_prevIterPos[0],m_prevIterPos[1]+0.3f,m_prevIterPos[2], steerCol);
                dd.vertex(m_steerPos[0],m_steerPos[1]+0.3f,m_steerPos[2], steerCol);

                for (int i = 0; i < m_steerPointCount-1; ++i)
                {
                    dd.vertex(m_steerPoints[i*3+0],m_steerPoints[i*3+1]+0.2f,m_steerPoints[i*3+2], duDarkenCol(steerCol));
                    dd.vertex(m_steerPoints[(i+1)*3+0],m_steerPoints[(i+1)*3+1]+0.2f,m_steerPoints[(i+1)*3+2], duDarkenCol(steerCol));
                }

                dd.end();
                dd.depthMask(true);
            }
            */
        } else if (m_toolMode == ToolMode.PATHFIND_STRAIGHT || m_toolMode == ToolMode.PATHFIND_SLICED) {
            dd.debugDrawNavMeshPoly(m_navMesh, m_startRef, startCol);
            dd.debugDrawNavMeshPoly(m_navMesh, m_endRef, endCol);

            if (m_polys != null) {
                for (Long poly : m_polys) {
                    dd.debugDrawNavMeshPoly(m_navMesh, poly, pathCol);
                }
            }
            if (m_straightPath != null) {
                dd.depthMask(false);
                int spathCol = DebugDraw.duRGBA(64, 16, 0, 220);
                int offMeshCol = DebugDraw.duRGBA(128, 96, 0, 220);
                dd.begin(DebugDrawPrimitives.LINES, 2.0f);
                for (int i = 0; i < m_straightPath.size() - 1; ++i) {
                    StraightPathItem straightPathItem = m_straightPath.get(i);
                    StraightPathItem straightPathItem2 = m_straightPath.get(i + 1);
                    int col;
                    if ((straightPathItem.getFlags() & NavMeshQuery.DT_STRAIGHTPATH_OFFMESH_CONNECTION) != 0) {
                        col = offMeshCol;
                    } else {
                        col = spathCol;
                    }
                    dd.vertex(straightPathItem.getPos()[0], straightPathItem.getPos()[1] + 0.4f, straightPathItem.getPos()[2], col);
                    dd.vertex(straightPathItem2.getPos()[0], straightPathItem2.getPos()[1] + 0.4f, straightPathItem2.getPos()[2], col);
                }
                dd.end();
                dd.begin(DebugDrawPrimitives.POINTS, 6.0f);
                for (int i = 0; i < m_straightPath.size(); ++i) {
                    StraightPathItem straightPathItem = m_straightPath.get(i);
                    int col;
                    if ((straightPathItem.getFlags() & NavMeshQuery.DT_STRAIGHTPATH_START) != 0) {
                        col = startCol;
                    } else if ((straightPathItem.getFlags() & NavMeshQuery.DT_STRAIGHTPATH_END) != 0) {
                        col = endCol;
                    } else if ((straightPathItem.getFlags() & NavMeshQuery.DT_STRAIGHTPATH_OFFMESH_CONNECTION) != 0) {
                        col = offMeshCol;
                    } else {
                        col = spathCol;
                    }
                    dd.vertex(straightPathItem.getPos()[0], straightPathItem.getPos()[1] + 0.4f, straightPathItem.getPos()[2], col);
                }
                dd.end();
                dd.depthMask(true);
            }
        } else if (m_toolMode == ToolMode.RAYCAST) {
            dd.debugDrawNavMeshPoly(m_navMesh, m_startRef, startCol);

            if (m_straightPath != null) {
                if (m_polys != null) {
                    for (Long poly : m_polys) {
                        dd.debugDrawNavMeshPoly(m_navMesh, poly, pathCol);
                    }
                }

                dd.depthMask(false);
                int spathCol = m_hitResult ? DebugDraw.duRGBA(64, 16, 0, 220) : DebugDraw.duRGBA(240, 240, 240, 220);
                dd.begin(DebugDrawPrimitives.LINES, 2.0f);
                for (int i = 0; i < m_straightPath.size() - 1; ++i) {
                    StraightPathItem straightPathItem = m_straightPath.get(i);
                    StraightPathItem straightPathItem2 = m_straightPath.get(i + 1);
                    dd.vertex(straightPathItem.getPos()[0], straightPathItem.getPos()[1] + 0.4f, straightPathItem.getPos()[2], spathCol);
                    dd.vertex(straightPathItem2.getPos()[0], straightPathItem2.getPos()[1] + 0.4f, straightPathItem2.getPos()[2], spathCol);
                }
                dd.end();
                dd.begin(DebugDrawPrimitives.POINTS, 4.0f);
                for (int i = 0; i < m_straightPath.size(); ++i) {
                    StraightPathItem straightPathItem = m_straightPath.get(i);
                    dd.vertex(straightPathItem.getPos()[0], straightPathItem.getPos()[1] + 0.4f, straightPathItem.getPos()[2], spathCol);
                }
                dd.end();

                if (m_hitResult) {
                    int hitCol = DebugDraw.duRGBA(0, 0, 0, 128);
                    dd.begin(DebugDrawPrimitives.LINES, 2.0f);
                    dd.vertex(m_hitPos[0], m_hitPos[1] + 0.4f, m_hitPos[2], hitCol);
                    dd.vertex(m_hitPos[0] + m_hitNormal[0] * agentRadius, m_hitPos[1] + 0.4f + m_hitNormal[1] * agentRadius,
                            m_hitPos[2] + m_hitNormal[2] * agentRadius, hitCol);
                    dd.end();
                }
                dd.depthMask(true);
            }
        } else if (m_toolMode == ToolMode.DISTANCE_TO_WALL) {
            dd.debugDrawNavMeshPoly(m_navMesh, m_startRef, startCol);
            dd.depthMask(false);
            if (m_spos != null) {
                dd.debugDrawCircle(m_spos[0], m_spos[1] + agentHeight / 2, m_spos[2], m_distanceToWall, DebugDraw.duRGBA(64, 16, 0, 220),
                        2.0f);
            }
            if (m_hitPos != null) {
                dd.begin(DebugDrawPrimitives.LINES, 3.0f);
                dd.vertex(m_hitPos[0], m_hitPos[1] + 0.02f, m_hitPos[2], DebugDraw.duRGBA(0, 0, 0, 192));
                dd.vertex(m_hitPos[0], m_hitPos[1] + agentHeight, m_hitPos[2], DebugDraw.duRGBA(0, 0, 0, 192));
                dd.end();
            }
            dd.depthMask(true);
        } else if (m_toolMode == ToolMode.FIND_POLYS_IN_CIRCLE) {
            if (m_polys != null) {
                for (int i = 0; i < m_polys.size(); i++) {
                    dd.debugDrawNavMeshPoly(m_navMesh, m_polys.get(i), pathCol);
                    dd.depthMask(false);
                    if (m_parent.get(i) != 0) {
                        dd.depthMask(false);
                        float[] p0 = getPolyCenter(m_navMesh, m_parent.get(i));
                        float[] p1 = getPolyCenter(m_navMesh, m_polys.get(i));
                        dd.debugDrawArc(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0.25f, 0.0f, 0.4f, DebugDraw.duRGBA(0, 0, 0, 128), 2.0f);
                        dd.depthMask(true);
                    }
                    dd.depthMask(true);
                }
            }

            if (m_sposSet && m_eposSet) {
                dd.depthMask(false);
                float dx = m_epos[0] - m_spos[0];
                float dz = m_epos[2] - m_spos[2];
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                dd.debugDrawCircle(m_spos[0], m_spos[1] + agentHeight / 2, m_spos[2], dist, DebugDraw.duRGBA(64, 16, 0, 220), 2.0f);
                dd.depthMask(true);
            }
        } else if (m_toolMode == ToolMode.FIND_POLYS_IN_SHAPE) {
            if (m_polys != null) {
                for (int i = 0; i < m_polys.size(); i++) {
                    dd.debugDrawNavMeshPoly(m_navMesh, m_polys.get(i), pathCol);
                    dd.depthMask(false);
                    if (m_parent.get(i) != 0) {
                        dd.depthMask(false);
                        float[] p0 = getPolyCenter(m_navMesh, m_parent.get(i));
                        float[] p1 = getPolyCenter(m_navMesh, m_polys.get(i));
                        dd.debugDrawArc(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0.25f, 0.0f, 0.4f, DebugDraw.duRGBA(0, 0, 0, 128), 2.0f);
                        dd.depthMask(true);
                    }
                    dd.depthMask(true);
                }
            }

            if (m_sposSet && m_eposSet) {
                dd.depthMask(false);
                int col = DebugDraw.duRGBA(64, 16, 0, 220);
                dd.begin(DebugDrawPrimitives.LINES, 2.0f);
                for (int i = 0, j = 3; i < 4; j = i++) {
                    dd.vertex(m_queryPoly[j * 3], m_queryPoly[j * 3 + 1], m_queryPoly[j * 3 + 2], col);
                    dd.vertex(m_queryPoly[i * 3], m_queryPoly[i * 3 + 1], m_queryPoly[i * 3 + 2], col);
                }
                dd.end();
                dd.depthMask(true);
            }
        } else if (m_toolMode == ToolMode.FIND_LOCAL_NEIGHBOURHOOD) {
            if (m_polys != null) {
                for (int i = 0; i < m_polys.size(); i++) {
                    dd.debugDrawNavMeshPoly(m_navMesh, m_polys.get(i), pathCol);
                    dd.depthMask(false);
                    if (m_parent.get(i) != 0) {
                        dd.depthMask(false);
                        float[] p0 = getPolyCenter(m_navMesh, m_parent.get(i));
                        float[] p1 = getPolyCenter(m_navMesh, m_polys.get(i));
                        dd.debugDrawArc(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0.25f, 0.0f, 0.4f, DebugDraw.duRGBA(0, 0, 0, 128), 2.0f);
                        dd.depthMask(true);
                    }
                    dd.depthMask(true);
                    if (m_sample.getNavMeshQuery() != null) {
                        // int MAX_SEGS = DT_VERTS_PER_POLYGON*4;
                        // float segs[MAX_SEGS*6];
                        // dtPolyRef refs[MAX_SEGS];
                        // memset(refs, 0, sizeof(dtPolyRef)*MAX_SEGS);
                        // int nsegs = 0;
                        GetPolyWallSegmentsResult result = m_sample.getNavMeshQuery().getPolyWallSegments(m_polys.get(i), false, m_filter);
                        dd.begin(DebugDrawPrimitives.LINES, 2.0f);

                        for (int j = 0; j < result.getSegmentVerts().size(); ++j) {
                            float[] s = result.getSegmentVerts().get(j);
                            //
                            // // Skip too distant segments.
                            // float tseg;
                            // float distSqr = dtDistancePtSegSqr2D(m_spos, s, s+3, tseg);
                            // if (distSqr > DemoMath.sqr(m_neighbourhoodRadius)) {
                            // continue;
                            // }
                            //
                            // float delta[3], norm[3], p0[3], p1[3];
                            // float delta = DemoMath.vSub(s, s)
                            // dtVsub(delta, s+3,s);
                            // dtVmad(p0, s, delta, 0.5f);
                            // norm[0] = delta[2];
                            // norm[1] = 0;
                            // norm[2] = -delta[0];
                            // dtVnormalize(norm);
                            // dtVmad(p1, p0, norm, agentRadius*0.5f);
                            //
                            // // Skip backfacing segments.
                            // if (refs[j])
                            // {
                            // unsigned int col = duRGBA(255,255,255,32);
                            // dd.vertex(s[0],s[1]+agentClimb,s[2],col);
                            // dd.vertex(s[3],s[4]+agentClimb,s[5],col);
                            // }
                            // else
                            // {
                            // unsigned int col = duRGBA(192,32,16,192);
                            // if (dtTriArea2D(m_spos, s, s+3) < 0.0f)
                            // col = duRGBA(96,32,16,192);
                            //
                            // dd.vertex(p0[0],p0[1]+agentClimb,p0[2],col);
                            // dd.vertex(p1[0],p1[1]+agentClimb,p1[2],col);
                            //
                            // dd.vertex(s[0],s[1]+agentClimb,s[2],col);
                            // dd.vertex(s[3],s[4]+agentClimb,s[5],col);
                        }
                    }
                    dd.end();

                    dd.depthMask(true);
                }

                if (m_sposSet) {
                    dd.depthMask(false);
                    dd.debugDrawCircle(m_spos[0], m_spos[1] + agentHeight / 2, m_spos[2], m_neighbourhoodRadius,
                            DebugDraw.duRGBA(64, 16, 0, 220), 2.0f);
                    dd.depthMask(true);
                }
            }
        }
    }

    private void drawAgent(RecastDebugDraw dd, float[] pos, float r, float h, float c, int col) {
        dd.depthMask(false);
        // Agent dimensions.
        dd.debugDrawCylinderWire(pos[0] - r, pos[1] + 0.02f, pos[2] - r, pos[0] + r, pos[1] + h, pos[2] + r, col, 2.0f);
        dd.debugDrawCircle(pos[0], pos[1] + c, pos[2], r, DebugDraw.duRGBA(0, 0, 0, 64), 1.0f);
        int colb = DebugDraw.duRGBA(0, 0, 0, 196);
        dd.begin(DebugDrawPrimitives.LINES);
        dd.vertex(pos[0], pos[1] - c, pos[2], colb);
        dd.vertex(pos[0], pos[1] + c, pos[2], colb);
        dd.vertex(pos[0] - r / 2, pos[1] + 0.02f, pos[2], colb);
        dd.vertex(pos[0] + r / 2, pos[1] + 0.02f, pos[2], colb);
        dd.vertex(pos[0], pos[1] + 0.02f, pos[2] - r / 2, colb);
        dd.vertex(pos[0], pos[1] + 0.02f, pos[2] + r / 2, colb);
        dd.end();
        dd.depthMask(true);
    }

    private float[] getPolyCenter(NavMesh navMesh, long ref) {
        float[] center = new float[3];
        center[0] = 0;
        center[1] = 0;
        center[2] = 0;
        try {
            Tupple2<MeshTile, Poly> tileAndPoly = navMesh.getTileAndPolyByRef(ref);
            MeshTile tile = tileAndPoly.first;
            Poly poly = tileAndPoly.second;
            for (int i = 0; i < poly.vertCount; ++i) {
                int v = poly.verts[i] * 3;
                center[0] += tile.data.verts[v];
                center[1] += tile.data.verts[v + 1];
                center[2] += tile.data.verts[v + 2];
            }
            float s = 1.0f / poly.vertCount;
            center[0] *= s;
            center[1] *= s;
            center[2] *= s;
        } catch (Exception e) {
        }
        return center;
    }
}
